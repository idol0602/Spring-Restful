package fa.training.restapi.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

public interface GenericService<T,ID> {
    List<T> findAll();
    Optional<T> findById(ID id);
    T save(T entity);
    void deleteById(ID id);
    boolean existsById(ID id);
    Page<T> findAll(Pageable pageable);
    
    List<T> saveAll(List<T> entities);
    void deleteAll(List<ID> ids);
    void exportToCsv(OutputStream outputStream);
    List<T> importFromCsv(MultipartFile file);
}
