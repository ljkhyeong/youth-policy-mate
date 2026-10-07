package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

/** 전체 건수 없이 다음 페이지 여부만 알려주는 관리자 목록. */
@Schema(requiredProperties = {"items", "page", "pageSize", "hasNext"})
public record AdminSlice<T>(List<T> items, @Schema(description = "1부터 시작하는 페이지") int page, int pageSize, boolean hasNext) {
    // SQL은 LIMIT :limit OFFSET :offset 을 포함해야 한다. 한 건 더 읽어 다음 페이지 여부만 판단하고 잘라낸다.
    static <T> AdminSlice<T> fetch(JdbcClient.StatementSpec sql, int page, int pageSize, RowMapper<T> rowMapper) {
        var rows = sql.param("limit", pageSize + 1).param("offset", (page - 1) * pageSize).query(rowMapper).list();
        var hasNext = rows.size() > pageSize;
        return new AdminSlice<>(hasNext ? rows.subList(0, pageSize) : rows, page, pageSize, hasNext);
    }
}
