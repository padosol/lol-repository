package com.mmrtr.lol.backfill;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * riot 스키마 테스트 행 삽입 헬퍼. 기본값 없는 NOT NULL 컬럼은 타입별 0 값으로 채우고,
 * 테스트에 필요한 컬럼만 지정한다. identity 컬럼이 있으면 생성된 id 를, 없으면 0 을 돌려준다.
 */
public class RiotTestRows {

    private final JdbcTemplate jdbc;

    public RiotTestRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String table, Map<String, Object> values) {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable, column_default, is_identity, is_generated
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ?
                """, table);
        Map<String, Object> row = new LinkedHashMap<>();
        String identityColumn = null;
        for (Map<String, Object> column : columns) {
            String name = (String) column.get("column_name");
            if ("YES".equals(column.get("is_identity"))) {
                identityColumn = name;
            } else if ("NO".equals(column.get("is_nullable")) && column.get("column_default") == null
                    && "NEVER".equals(column.get("is_generated"))) {
                row.put(name, zeroOf((String) column.get("data_type")));
            }
        }
        row.putAll(values);

        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < row.size(); i++) {
            placeholders.add("?");
        }
        String sql = "INSERT INTO " + table + " (" + String.join(", ", row.keySet())
                + ") VALUES (" + String.join(", ", placeholders) + ")";
        KeyHolder keys = new GeneratedKeyHolder();
        String keyColumn = identityColumn;
        jdbc.update(connection -> {
            PreparedStatement ps = keyColumn == null
                    ? connection.prepareStatement(sql, Statement.NO_GENERATED_KEYS)
                    : connection.prepareStatement(sql, new String[] {keyColumn});
            int position = 1;
            for (Object value : row.values()) {
                ps.setObject(position++, value);
            }
            return ps;
        }, keys);
        if (keyColumn == null) {
            return 0L;
        }
        return ((Number) keys.getKeys().get(keyColumn)).longValue();
    }

    private static Object zeroOf(String dataType) {
        return switch (dataType) {
            case "boolean" -> false;
            case "smallint", "integer" -> 0;
            case "bigint" -> 0L;
            case "real", "double precision", "numeric" -> 0.0;
            default -> "";
        };
    }
}
