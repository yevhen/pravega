/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.segmentstore.storage.rolling;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.pravega.common.io.EnhancedByteArrayOutputStream;
import io.pravega.common.util.ByteArraySegment;
import io.pravega.segmentstore.storage.SegmentHandle;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import lombok.SneakyThrows;
import lombok.val;

final class HandleSerializer {
    private static final Charset ENCODING = Charsets.UTF_8;
    private static final String KEY_NAME = "name";
    private static final String KEY_POLICY_MAX_SIZE = "maxsize";
    private static final String KEY_VALUE_SEPARATOR = "=";
    private static final String SEPARATOR = "&";

    static RollingSegmentHandle deserialize(byte[] serialization, SegmentHandle headerHandle) {
        StringTokenizer st = new StringTokenizer(new String(serialization, ENCODING), SEPARATOR, false);
        Preconditions.checkArgument(st.hasMoreTokens(), "No separators in serialization.");

        // 1. Segment Name.
        val nameEntry = parse(st.nextToken(), KEY_NAME::equals, HandleSerializer::nonEmpty);
        // 2. Policy Max Size.
        val policyEntry = parse(st.nextToken(), KEY_POLICY_MAX_SIZE::equals, HandleSerializer::isValidLong);
        // 3. SubSegments.
        ArrayList<SubSegment> subSegments = new ArrayList<>();
        long lastOffset = -1;
        while (st.hasMoreTokens()) {
            val entry = parse(st.nextToken(), HandleSerializer::isValidLong, HandleSerializer::nonEmpty);
            SubSegment s = parse(entry);
            Preconditions.checkArgument(lastOffset < s.getStartOffset(),
                    "SubSegment Entry '%s' has out-of-order offset (previous=%s).", s, lastOffset);
            subSegments.add(s);
            lastOffset = s.getStartOffset();
        }

        val result = new RollingSegmentHandle(nameEntry.getValue(), headerHandle,
                new SegmentRollingPolicy(Long.parseLong(policyEntry.getValue())), subSegments);
        result.setSerializedHeaderLength(serialization.length);
        return result;
    }

    /**
     * Serializes a single SubSegment into a String
     */
    static byte[] serialize(SubSegment subSegment) {
        return combine(Long.toString(subSegment.getStartOffset()), subSegment.getName());
    }

    /**
     * Serializes an entire RollingSegmentHandle into a new ByteArraySegment.
     *
     * @param handle
     * @return
     */
    @SneakyThrows(IOException.class)
    static ByteArraySegment serialize(RollingSegmentHandle handle) {
        try (EnhancedByteArrayOutputStream os = new EnhancedByteArrayOutputStream()) {
            //1. Segment Name.
            os.write(combine(KEY_NAME, handle.getSegmentName()));
            //2. Policy Max Size.
            os.write(combine(KEY_POLICY_MAX_SIZE, Long.toString(handle.getRollingPolicy().getMaxLength())));
            //3. SubSegments.
            handle.subSegments().forEach(subSegment -> os.write(serialize(subSegment)));
            return os.getData();
        }
    }

    private static byte[] combine(String key, String value) {
        return (key + KEY_VALUE_SEPARATOR + value + SEPARATOR).getBytes(ENCODING);
    }

    private static Map.Entry<String, String> parse(String entry, Predicate<String> keyValidator, Predicate<String> valueValidator) {
        int sp = entry.indexOf(KEY_VALUE_SEPARATOR);
        Preconditions.checkArgument(sp > 0 && sp < entry.length() - 1, "Header entry '%s' is invalid.", entry);

        String key = entry.substring(0, sp);
        Preconditions.checkArgument(keyValidator.test(key), "Invalid entry key for '%s'.", entry);

        String value = entry.substring(sp + 1);
        Preconditions.checkArgument(valueValidator.test(value), "Invalid entry value for '%s'.", entry);
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static SubSegment parse(Map.Entry<String, String> entry) {
        return new SubSegment(entry.getValue(), Long.parseLong(entry.getKey()));
    }

    private static boolean nonEmpty(String s) {
        return !Strings.isNullOrEmpty(s);
    }

    private static boolean isValidLong(String s) {
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }
}
