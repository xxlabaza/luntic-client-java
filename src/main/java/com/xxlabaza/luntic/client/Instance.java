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

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xxlabaza.luntic.client.Instance.Deserializer;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

/**
 * This object represents discovery service record.
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since Jul 24, 2017
 */
@Value
@Builder
@JsonDeserialize(using = Deserializer.class)
public class Instance {

    String id;

    String group;

    ZonedDateTime created;

    ZonedDateTime modified;

    Optional<Map<String, Object>> meta;

    static class Deserializer extends JsonDeserializer<Instance> {

        @Override
        public Instance deserialize (JsonParser parser, DeserializationContext context)
                throws IOException, JsonProcessingException {

            ObjectCodec codec = parser.getCodec();
            JsonNode node = codec.readTree(parser);

            InstanceBuilder builder = Instance.builder()
                    .id(node.get("id").asText())
                    .group(node.get("group").asText())
                    .created(ZonedDateTime.parse(node.get("created").asText(), ISO_OFFSET_DATE_TIME))
                    .modified(ZonedDateTime.parse(node.get("modified").asText(), ISO_OFFSET_DATE_TIME));

            Optional<Map<String, Object>> optional;
            if (node.hasNonNull("meta")) {
                Map<String, Object> meta = codec.treeToValue(node.get("meta"), Map.class);
                optional = Optional.ofNullable(meta);
            } else {
                optional = Optional.empty();
            }
            builder.meta(optional);

            return builder.build();
        }

    }
}
