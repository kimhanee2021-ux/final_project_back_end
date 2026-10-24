package org.ict.final_project_back_end.domain.gamenews;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class BbsLocalSchemaTest {
  @Test void preparesSeparateEmptyDatabaseAndPreservesExistingRowsWhenRepeated() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:h2:mem:bbs-demo-" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
      var script = new FileSystemResource("docs/db/bbs-local-demo.sql");
      ScriptUtils.executeSqlScript(c, script);
      try (var s = c.createStatement()) {
        try (var r = s.executeQuery("SELECT COUNT(*) FROM games")) { r.next(); assertEquals(3, r.getInt(1)); }
        try (var r = s.executeQuery("SELECT COUNT(*) FROM game_search_names")) { r.next(); assertEquals(4, r.getInt(1)); }
        s.executeUpdate("UPDATE games SET name='Existing name',description='KEEP' WHERE app_id=730");
        s.executeUpdate("UPDATE game_search_names SET active=0 WHERE app_id=730");
        ScriptUtils.executeSqlScript(c, script);
        try (var r = s.executeQuery("SELECT name,description FROM games WHERE app_id=730")) {
          r.next(); assertEquals("Existing name", r.getString(1)); assertEquals("KEEP", r.getString(2));
        }
        try (var r = s.executeQuery("SELECT COUNT(*) FROM game_search_names WHERE app_id=730 AND active=0")) { r.next(); assertEquals(2, r.getInt(1)); }
        assertThrows(SQLException.class, () -> s.executeUpdate("DELETE FROM games WHERE app_id=730"));
      }
    }
  }
}
