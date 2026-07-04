package fa.training.restapi.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageRequestDto {
    private int pageNo = 1;
    private int pageSize = 10;
    private String sortBy = "id";
    private String sortDir = "asc";
}
