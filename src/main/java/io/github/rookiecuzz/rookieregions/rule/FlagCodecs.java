package io.github.rookiecuzz.rookieregions.rule;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class FlagCodecs {
    public static final FlagCodec<State> STATE = new FlagCodec<>() {
        @Override
        public Object encode(State value) {
            return value.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public State decode(Object encoded) {
            if(!(encoded instanceof String value)){
                throw new IllegalArgumentException("state flag must be a string");
            }
            try {
                return State.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error){
                throw new IllegalArgumentException("invalid state flag: " + value, error);
            }
        }
    };

    public static final FlagCodec<String> STRING = new FlagCodec<>() {
        @Override
        public Object encode(String value) {
            return value;
        }

        @Override
        public String decode(Object encoded) {
            if(!(encoded instanceof String value)){
                throw new IllegalArgumentException("string flag must be a string");
            }
            return value;
        }
    };

    public static final FlagCodec<Integer> INTEGER = new FlagCodec<>() {
        @Override
        public Object encode(Integer value) {
            return value;
        }

        @Override
        public Integer decode(Object encoded) {
            if(!(encoded instanceof Number value)){
                throw new IllegalArgumentException("integer flag must be a number");
            }
            long result = value.longValue();
            if(result < Integer.MIN_VALUE || result > Integer.MAX_VALUE
                    || value.doubleValue() != result){
                throw new IllegalArgumentException("integer flag is outside int range");
            }
            return (int) result;
        }
    };

    public static final FlagCodec<Set<String>> STRING_SET = new FlagCodec<>() {
        @Override
        public Object encode(Set<String> value) {
            return value.stream().sorted().toList();
        }

        @Override
        public Set<String> decode(Object encoded) {
            if(!(encoded instanceof Iterable<?> values)){
                throw new IllegalArgumentException("set flag must be a list");
            }
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for(Object value : values){
                if(!(value instanceof String string)){
                    throw new IllegalArgumentException("string set contains a non-string value");
                }
                result.add(string);
            }
            return Set.copyOf(result);
        }
    };

    private FlagCodecs() {
    }
}
