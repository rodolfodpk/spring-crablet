package com.crablet.eventstore.period;

import com.crablet.eventstore.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PeriodTags} constants and factory methods.
 */
@DisplayName("PeriodTags Unit Tests")
class PeriodTagsTest {

    // ===== Constants =====

    @Test
    @DisplayName("Constants should have the correct string values")
    void constantsShouldHaveCorrectValues() {
        assertThat(PeriodTags.YEAR).isEqualTo("year");
        assertThat(PeriodTags.MONTH).isEqualTo("month");
        assertThat(PeriodTags.DAY).isEqualTo("day");
        assertThat(PeriodTags.HOUR).isEqualTo("hour");
    }

    // ===== Factory methods =====

    @Test
    @DisplayName("yearly() should return one tag: year")
    void yearlyShouldReturnOneTag() {
        List<Tag> tags = PeriodTags.yearly(2024);

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).key()).isEqualTo("year");
        assertThat(tags.get(0).value()).isEqualTo("2024");
    }

    @Test
    @DisplayName("monthly() should return two tags: year and month")
    void monthlyShouldReturnTwoTags() {
        List<Tag> tags = PeriodTags.monthly(2024, 3);

        assertThat(tags).hasSize(2);
        assertThat(tags).extracting(Tag::key).containsExactly("year", "month");
        assertThat(tags).extracting(Tag::value).containsExactly("2024", "3");
    }

    @Test
    @DisplayName("daily() should return three tags: year, month, day")
    void dailyShouldReturnThreeTags() {
        List<Tag> tags = PeriodTags.daily(2024, 3, 15);

        assertThat(tags).hasSize(3);
        assertThat(tags).extracting(Tag::key).containsExactly("year", "month", "day");
        assertThat(tags).extracting(Tag::value).containsExactly("2024", "3", "15");
    }

    @Test
    @DisplayName("hourly() should return four tags: year, month, day, hour")
    void hourlyShouldReturnFourTags() {
        List<Tag> tags = PeriodTags.hourly(2024, 3, 15, 9);

        assertThat(tags).hasSize(4);
        assertThat(tags).extracting(Tag::key).containsExactly("year", "month", "day", "hour");
        assertThat(tags).extracting(Tag::value).containsExactly("2024", "3", "15", "9");
    }

    @Test
    @DisplayName("monthly() boundary — month 1 and month 12")
    void monthlyBoundaryValues() {
        List<Tag> jan = PeriodTags.monthly(2024, 1);
        List<Tag> dec = PeriodTags.monthly(2024, 12);

        assertThat(jan).extracting(Tag::value).containsExactly("2024", "1");
        assertThat(dec).extracting(Tag::value).containsExactly("2024", "12");
    }

    @Test
    @DisplayName("hourly() boundary — hour 0 and hour 23")
    void hourlyBoundaryValues() {
        List<Tag> midnight = PeriodTags.hourly(2024, 1, 1, 0);
        List<Tag> lastHour = PeriodTags.hourly(2024, 12, 31, 23);

        assertThat(midnight).extracting(Tag::value).containsExactly("2024", "1", "1", "0");
        assertThat(lastHour).extracting(Tag::value).containsExactly("2024", "12", "31", "23");
    }

    @Test
    @DisplayName("Tags returned by factory methods should be immutable lists")
    void factoryMethodsReturnImmutableLists() {
        List<Tag> tags = PeriodTags.monthly(2024, 6);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tags.add(Tag.of("extra", "val")))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
