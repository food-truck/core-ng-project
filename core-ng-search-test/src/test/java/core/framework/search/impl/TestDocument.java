package core.framework.search.impl;

import core.framework.api.json.Property;
import core.framework.api.validate.NotNull;
import core.framework.search.Index;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * @author neo
 */
@Index(name = "document")
public class TestDocument {
    @NotNull
    @Property(name = "id")
    public String id;

    @Property(name = "completion1")
    public String completion1;

    @Property(name = "completion2")
    public String completion2;

    @Property(name = "string_field")
    public String stringField;

    @Property(name = "int_field")
    public Integer intField;

    @Property(name = "double_field")
    public Double doubleField;

    @Property(name = "zoned_date_time_field")
    public ZonedDateTime zonedDateTimeField;

    @Property(name = "local_time_field")
    public LocalTime localTimeField;

    @Property(name = "enum_field")
    public TestEnum enumField;

    @Property(name = "nested")
    public List<Nested> nested;

    public enum TestEnum {
        @Property(name = "V1")
        VALUE1,
        @Property(name = "V2")
        VALUE2
    }

    public static class Nested {
        @Property(name = "field1")
        public String field1;

        @Property(name = "field2")
        public String field2;
    }
}
