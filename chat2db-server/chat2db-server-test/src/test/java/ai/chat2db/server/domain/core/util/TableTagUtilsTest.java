package ai.chat2db.server.domain.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TableTagUtilsTest {

    @Test
    void shouldKeepTheLongestSharedPrefixForASingleTag() {
        Map<String, String> tags = TableTagUtils.calculateTags(List.of("order_item_main", "order_item_detail"));

        assertEquals("order_item", tags.get("order_item_main"));
        assertEquals("order_item", tags.get("order_item_detail"));
    }

    @Test
    void shouldKeepSiblingTagsAsLongAsPossible() {
        Map<String, String> tags = TableTagUtils.calculateTags(List.of(
                "order_item_main", "order_item_detail", "order_log_main", "order_log_detail"));

        assertEquals("order_item", tags.get("order_item_main"));
        assertEquals("order_item", tags.get("order_item_detail"));
        assertEquals("order_log", tags.get("order_log_main"));
        assertEquals("order_log", tags.get("order_log_detail"));
    }

    @Test
    void shouldRemoveShorterTagWhenTagsContainEachOther() {
        Map<String, String> tags = TableTagUtils.calculateTags(List.of(
                "order_main", "order_detail", "order_item_main", "order_item_detail"));

        assertNull(tags.get("order_main"));
        assertNull(tags.get("order_detail"));
        assertEquals("order_item", tags.get("order_item_main"));
        assertEquals("order_item", tags.get("order_item_detail"));
    }

    @Test
    void shouldNotMergeUnrelatedTags() {
        Map<String, String> tags = TableTagUtils.calculateTags(List.of(
                "order_item_main", "order_item_detail", "user_profile_main", "user_profile_detail", "standalone"));

        assertEquals("order_item", tags.get("order_item_main"));
        assertEquals("user_profile", tags.get("user_profile_main"));
        assertNull(tags.get("standalone"));
    }

    @Test
    void shouldOnlyCutTagsAtSupportedSeparators() {
        Map<String, String> tags = TableTagUtils.calculateTags(List.of(
                "audit-log-main", "audit-log-detail", "audit.logger.main", "audit.logger.detail"));

        assertEquals("audit-log", tags.get("audit-log-main"));
        assertEquals("audit-log", tags.get("audit-log-detail"));
        assertEquals("audit.logger", tags.get("audit.logger.main"));
        assertEquals("audit.logger", tags.get("audit.logger.detail"));
    }

    @Test
    void shouldAllowACompleteTableNameAsTag() {
        Map<String, String> tags = TableTagUtils.calculateTags(List.of("user", "user_detail", "standalone"));

        assertEquals("user", tags.get("user"));
        assertEquals("user", tags.get("user_detail"));
        assertNull(tags.get("standalone"));
    }

    @Test
    void shouldCountUniqueTablesWhenUsingACompleteNameAsTag() {
        Map<String, String> tags = TableTagUtils.calculateTags(List.of("standalone", "STANDALONE", "standalone"));

        assertNull(tags.get("standalone"));
    }
}
