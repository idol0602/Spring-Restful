package fa.training.restapi.service.impl;

import fa.training.restapi.exception.AppException;
import fa.training.restapi.exception.ErrorCode;
import fa.training.restapi.service.GenericService;
import jakarta.persistence.*;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.GenericTypeResolver;
import org.springframework.web.multipart.MultipartFile;

public abstract class GenericServiceImpl<T, ID> implements GenericService<T, ID> {

    @PersistenceContext
    protected EntityManager entityManager;

    protected final JpaRepository<T, ID> repository;

    protected GenericServiceImpl(JpaRepository<T, ID> repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<T> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<T> findById(ID id) {
        return repository.findById(id);
    }

    @Override
    @Transactional
    public T save(T entity) {
        ID id = getEntityId(entity);

        if (id == null) {
            return repository.save(entity);
        } else {
            Optional<T> dbEntityOpt = repository.findById(id);

            if (dbEntityOpt.isPresent()) {
                T managedEntity = dbEntityOpt.get();

                copyNonNullProperties(entity, managedEntity);

                return repository.save(managedEntity);
            } else {
                if (isIdGenerated(entity)) {
                    throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy bản ghi cần cập nhật với ID: " + id);
                } else {
                    return repository.save(entity);
                }
            }
        }
    }

    @Override
    public void deleteById(ID id) {
        if (!repository.existsById(id)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy bản ghi cần xóa với ID: " + id);
        }
        repository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Override
    public boolean existsById(ID id) {
        return repository.existsById(id);
    }

    @Override
    @Transactional
    public void saveAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        int batchSize = 50;
        for (int i = 0; i < entities.size(); i++) {
            repository.save(entities.get(i));
            
            if (i > 0 && i % batchSize == 0) {
                repository.flush();
                entityManager.clear();
            }
        }
        repository.flush();
        entityManager.clear();
    }

    @Override
    @Transactional
    public void deleteAll(List<ID> ids) {
        if (ids != null && !ids.isEmpty()) {
            repository.deleteAllById(ids);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void exportToCsv(OutputStream outputStream) {
        Class<?> clazz = (Class<?>) GenericTypeResolver.resolveTypeArguments(getClass(), GenericServiceImpl.class)[0];
        if (clazz == null) {
            throw new RuntimeException("Cannot resolve generic type for CSV export");
        }
        Field[] fields = clazz.getDeclaredFields();
        
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)))) {
            writer.write('\ufeff');
            for (int i = 0; i < fields.length; i++) {
                writer.print(fields[i].getName());
                if (i < fields.length - 1) {
                    writer.print(",");
                }
            }
            writer.println();
            
            int page = 0;
            int size = 500;
            Page<T> entityPage;
            do {
                entityPage = repository.findAll(PageRequest.of(page, size));
                for (T entity : entityPage.getContent()) {
                    for (int i = 0; i < fields.length; i++) {
                        fields[i].setAccessible(true);
                        try {
                            Object value = fields[i].get(entity);
                            writer.print(escapeSpecialCharacters(value != null ? value.toString() : ""));
                        } catch (IllegalAccessException e) {
                            writer.print("");
                        }
                        if (i < fields.length - 1) {
                            writer.print(",");
                        }
                    }
                    writer.println();
                }
                entityManager.clear();
                page++;
            } while (entityPage.hasNext());
        }
    }

    private String escapeSpecialCharacters(String data) {
        if (data == null) return "";
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    @Override
    @Transactional
    public void importFromCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty or null");
        }
        
        Class<?> clazz = (Class<?>) GenericTypeResolver.resolveTypeArguments(getClass(), GenericServiceImpl.class)[0];
        if (clazz == null) {
            throw new RuntimeException("Cannot resolve generic type for CSV import");
        }

        List<T> entities = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1); // Handle BOM
            }

            String[] headers = headerLine.split(",");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                @SuppressWarnings("unchecked")
                T entity = (T) clazz.getDeclaredConstructor().newInstance();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    String header = headers[i].trim();
                    String value = values[i].trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1).replace("\"\"", "\"");
                    }

                    try {
                        Field field = clazz.getDeclaredField(header);
                        field.setAccessible(true);
                        Object convertedValue = convertValue(value, field.getType());
                        field.set(entity, convertedValue);
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                entities.add(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to import CSV", e);
        }

        saveAll(entities);
    }

    private Object convertValue(String value, Class<?> type) {
        if (value == null || value.isEmpty()) return null;
        if (type == String.class) return value;
        if (type == Integer.class || type == int.class) return Integer.parseInt(value);
        if (type == Long.class || type == long.class) return Long.parseLong(value);
        if (type == Double.class || type == double.class) return Double.parseDouble(value);
        if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(value);
        return value;
    }

    @SuppressWarnings("unchecked")
    private ID getEntityId(T entity) {
        if (entity == null)
            return null;

        Class<?> clazz = entity.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.persistence.Id.class) ||
                        field.isAnnotationPresent(jakarta.persistence.EmbeddedId.class)) {
                    try {
                        field.setAccessible(true);
                        return (ID) field.get(entity);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Failed to access ID field of " + clazz.getSimpleName(), e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        throw new IllegalArgumentException(
                "No ID field found annotated with @Id in " + entity.getClass().getSimpleName());
    }

    private boolean isIdGenerated(T entity) {
        if (entity == null)
            return false;
        Class<?> clazz = entity.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    return field.isAnnotationPresent(GeneratedValue.class);
                }
                if (field.isAnnotationPresent(EmbeddedId.class)) {
                    return false;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    protected void copyNonNullProperties(Object source, Object target) {
        BeanUtils.copyProperties(source, target, getIgnoredPropertyNames(source));
        updateJpaRelationships(source, target);
    }

    private String[] getIgnoredPropertyNames(Object source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        PropertyDescriptor[] pds = src.getPropertyDescriptors();
        Set<String> ignoredNames = new HashSet<>();

        Set<String> jpaRelationFields = getJpaRelationFieldNames(source.getClass());

        for (PropertyDescriptor pd : pds) {
            String name = pd.getName();
            Object srcValue = src.getPropertyValue(name);

            if (srcValue == null) {
                ignoredNames.add(name);
                continue;
            }

            if (jpaRelationFields.contains(name)) {
                ignoredNames.add(name);
            }
        }

        return ignoredNames.toArray(new String[0]);
    }

    private Set<String> getJpaRelationFieldNames(Class<?> clazz) {
        Set<String> names = new HashSet<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (isJpaRelationField(field)) {
                    names.add(field.getName());
                }
            }
            current = current.getSuperclass();
        }
        return names;
    }

    private boolean isJpaRelationField(Field field) {
        return field.isAnnotationPresent(ManyToOne.class)
                || field.isAnnotationPresent(OneToOne.class)
                || field.isAnnotationPresent(OneToMany.class)
                || field.isAnnotationPresent(ManyToMany.class);
    }

    private void updateJpaRelationships(Object source, Object target) {
        Class<?> clazz = source.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!isJpaRelationField(field)) {
                    continue;
                }

                field.setAccessible(true);
                Object srcValue;
                try {
                    srcValue = field.get(source);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Cannot read field: " + field.getName(), e);
                }

                if (srcValue == null) {
                    continue;
                }

                if (field.isAnnotationPresent(ManyToOne.class)
                        || field.isAnnotationPresent(OneToOne.class)) {
                    try {
                        field.set(target, srcValue);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Cannot set field: " + field.getName(), e);
                    }
                } else if (field.isAnnotationPresent(OneToMany.class)
                        || field.isAnnotationPresent(ManyToMany.class)) {
                    syncCollection(field, srcValue, target);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    @SuppressWarnings("unchecked")
    private void syncCollection(Field field, Object srcValue, Object target) {
        if (!(srcValue instanceof Collection)) {
            return;
        }

        Collection<?> srcCollection = (Collection<?>) srcValue;

        Object trgValue;
        try {
            field.setAccessible(true);
            trgValue = field.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot read field: " + field.getName(), e);
        }

        if (!(trgValue instanceof Collection)) {
            return;
        }

        Collection<Object> trgCollection = (Collection<Object>) trgValue;

        trgCollection.clear();

        boolean isBidirectional = field.isAnnotationPresent(OneToMany.class);
        for (Object element : srcCollection) {
            if (element != null) {
                if (isBidirectional) {
                    setBackReference(element, target);
                }
                trgCollection.add(element);
            }
        }
    }

    private void setBackReference(Object child, Object parent) {
        Class<?> childClass = child.getClass();
        Class<?> parentClass = parent.getClass();

        while (childClass != null && childClass != Object.class) {
            for (Field field : childClass.getDeclaredFields()) {
                if (field.getType().isAssignableFrom(parentClass)
                        && (field.isAnnotationPresent(ManyToOne.class)
                        || field.isAnnotationPresent(OneToOne.class))) {
                    try {
                        field.setAccessible(true);
                        field.set(child, parent);
                        return;
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(
                                "Cannot set back-reference on " + childClass.getSimpleName() + "." + field.getName(),
                                e);
                    }
                }
            }
            childClass = childClass.getSuperclass();
        }
    }
}