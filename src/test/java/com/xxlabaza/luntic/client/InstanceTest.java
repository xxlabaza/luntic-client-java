/*
 * Copyright 2017 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xxlabaza.luntic.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import org.junit.Test;

/**
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since Jul 24, 2017
 */
public class InstanceTest {

    private static final ObjectMapper MAPPER;

    private static final JavaType INSTANCES_LIST_TYPE;

    private static final JavaType INSTANCES_MAP_TYPE;

    static {
        MAPPER = new ObjectMapper();

        INSTANCES_LIST_TYPE = MAPPER.getTypeFactory().
                constructCollectionType(List.class, Instance.class);

        INSTANCES_MAP_TYPE = MAPPER.getTypeFactory().
                constructMapType(Map.class, MAPPER.getTypeFactory().constructType(String.class), INSTANCES_LIST_TYPE);
    }

    @Test
    public void simpleDeserialization () throws IOException {
        Json expected = Json.builder()
                .id("ABCD123")
                .group("popa")
                .created("2017-07-22T03:13:35+03:00")
                .modified("2017-07-22T03:13:35+03:00")
                .build();

        Instance result = MAPPER.readValue(expected.toString(), Instance.class);
        assertFalse(result.getMeta().isPresent());
        assertInstance(expected, result);
    }

    @Test
    public void deserializationWithMeta () throws IOException {
        Map<String, Object> meta = new HashMap<>(2, 1.F);
        meta.put("name", "Artem");
        meta.put("age", "26");

        Json expected = Json.builder()
                .id("ABCD123")
                .group("popa")
                .created("2017-07-22T03:13:35+03:00")
                .modified("2017-07-22T03:13:35+03:00")
                .meta(meta)
                .build();

        Instance result = MAPPER.readValue(expected.toString(), Instance.class);
        assertNotNull(result.getMeta());
        assertInstance(expected, result);
    }

    @Test
    public void deserializationList () throws IOException {
        List<Json> expectedList = Arrays.asList(
                Json.builder()
                        .id("ABCD123")
                        .group("popa")
                        .created("2017-07-22T03:13:35+03:00")
                        .modified("2017-07-22T03:13:35+03:00")
                        .build(),
                Json.builder()
                        .id("ABCD124")
                        .group("popa")
                        .created("2017-07-22T03:13:37+03:00")
                        .modified("2017-07-22T03:13:37+03:00")
                        .build()
        );
        String json = Json.toJson(expectedList);

        List<Instance> result = MAPPER.readValue(json, INSTANCES_LIST_TYPE);

        assertInstances(expectedList, result);
    }

    @Test
    public void deserializeMap () throws IOException {
        Map<String, List<Json>> expectedMap = new HashMap<>(2, 1.F);
        expectedMap.put("popa", Arrays.asList(
                        Json.builder()
                                .id("ABC123")
                                .group("popa")
                                .created("2017-07-22T05:23:17+03:00")
                                .modified("2017-07-22T05:23:17+03:00")
                                .build(),
                        Json.builder()
                                .id("ABC124")
                                .group("popa")
                                .created("2017-08-22T05:13:17+03:00")
                                .modified("2017-08-22T05:13:17+03:00")
                                .build()
                ));
        expectedMap.put("zuul", Arrays.asList(
                        Json.builder()
                                .id("ZYW543")
                                .group("zuul")
                                .created("2017-07-22T05:13:17+03:00")
                                .modified("2017-07-22T05:13:17+03:00")
                                .build())
        );

        String json = Json.toJson(expectedMap);

        Map<String, List<Instance>> result = MAPPER.readValue(json, INSTANCES_MAP_TYPE);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(expectedMap.size(), result.size());
        expectedMap.entrySet().forEach(it -> {
            List<Instance> list = result.get(it.getKey());
            assertInstances(it.getValue(), list);
        });
    }

    private void assertInstance (Json expected, Instance instance) {
        assertNotNull(instance);

        assertEquals(expected.getId(), instance.getId());
        assertEquals(expected.getGroup(), instance.getGroup());
        assertEquals(expected.getCreated(), instance.getCreated().toString());
        assertEquals(expected.getModified(), instance.getModified().toString());

        if (expected.getMeta() != null) {
            assertTrue(instance.getMeta().isPresent());
            Map<String, Object> meta = instance.getMeta().get();
            assertEquals(expected.getMeta(), meta);
        } else {
            assertFalse(instance.getMeta().isPresent());
        }
    }

    private void assertInstances (List<Json> expected, List<Instance> instances) {
        assertNotNull(instances);
        assertFalse(instances.isEmpty());
        for (int i = 0; i < instances.size(); i++) {
            assertInstance(expected.get(i), instances.get(i));
        }
    }

    @Value
    @Builder
    private static class Json {

        private static String toJson (List<Json> list) {
            return list.stream().map(Json::toString).collect(Collectors.joining(", ", "[", "]"));
        }

        private static String toJson (Map<String, List<Json>> map) {
            return map.entrySet().stream()
                    .map(it -> new StringBuilder()
                            .append("\"").append(it.getKey()).append("\": ")
                            .append(toJson(it.getValue()))
                            .toString()
                    )
                    .collect(Collectors.joining(", ", "{", "}"));
        }

        String id;

        String group;

        String created;

        String modified;

        Map<String, Object> meta;

        @Override
        public String toString () {
            StringBuilder result = new StringBuilder("{")
                    .append("\"id\": \"").append(id).append("\", ")
                    .append("\"group\": \"").append(group).append("\", ")
                    .append("\"created\": \"").append(created).append("\", ")
                    .append("\"modified\": \"").append(modified).append("\"");

            if (meta != null && !meta.isEmpty()) {
                String metaString = meta.entrySet().stream()
                        .map(it -> new StringBuilder()
                                .append("\"").append(it.getKey()).append("\": \"").append(it.getValue()).append("\"")
                                .toString()
                        )
                        .collect(Collectors.joining(", "));

                result.append(", \"meta\": {").append(metaString).append('}');
            }

            return result
                    .append('}')
                    .toString();
        }

    }
}
