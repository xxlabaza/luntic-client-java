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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since Jul 25, 2017
 */
@RestController
@SpringBootApplication
public class Server {

    private static final Map<String, List<Instance>> REPOSITORY;

    static {
        REPOSITORY = new ConcurrentHashMap<>(5, 1.F);
    }

    private ScheduledExecutorService cleanerExecutor;

    private long expired;

    @PostMapping("/{group}")
    @ResponseStatus(CREATED)
    public Instance create (@PathVariable("group") String group,
                            @RequestBody(required = false) Map<String, Object> meta,
                            HttpServletResponse response
    ) {
        String id = UUID.randomUUID().toString();

        response.addHeader("Location", '/' + group + '/' + id);
        response.addHeader("X-Expired-Time", Long.toString(expired));

        Instance instance = Instance.builder()
                .id(id)
                .group(group)
                .created(ZonedDateTime.now())
                .modified(ZonedDateTime.now())
                .meta(Optional.ofNullable(meta))
                .build();

        REPOSITORY.putIfAbsent(group, new ArrayList<>());
        REPOSITORY.get(group).add(instance);

        return instance;
    }

    @GetMapping("/")
    public Map<String, List<Instance>> read () {
        return REPOSITORY;
    }

    @GetMapping("/{group}")
    public List<Instance> read (@PathVariable("group") String group, HttpServletResponse response) {
        List<Instance> result = REPOSITORY.get(group);
        if (result == null) {
            response.setStatus(NOT_FOUND.value());
        }
        return result;
    }

    @GetMapping("/{group}/{id}")
    public Instance read (@PathVariable("group") String group,
                          @PathVariable("id") String id,
                          HttpServletResponse response
    ) {
        Instance result = find(group, id);
        if (result == null) {
            response.setStatus(NOT_FOUND.value());
        }
        return result;
    }

    @PutMapping("/{group}/{id}")
    public Instance update (@PathVariable("group") String group,
                            @PathVariable("id") String id,
                            @RequestBody(required = false) Map<String, Object> meta,
                            HttpServletResponse response
    ) {
        List<Instance> instances = REPOSITORY.get(group);
        if (instances == null) {
            response.setStatus(NOT_FOUND.value());
            return null;
        }

        for (int i = 0; i < instances.size(); i++) {
            Instance instance = instances.get(i);
            if (instance.getId().equals(id)) {
                Instance.InstanceBuilder builder = Instance.builder()
                        .id(instance.getId())
                        .group(instance.getGroup())
                        .created(instance.getCreated())
                        .modified(ZonedDateTime.now());

                if (meta != null) {
                    builder.meta(Optional.of(meta));
                } else {
                    builder.meta(instance.getMeta());
                }

                Instance updated = builder.build();
                instances.set(i, updated);
                return updated;
            }
        }
        response.setStatus(NOT_FOUND.value());
        return null;
    }

    @DeleteMapping("/{group}/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete (@PathVariable("group") String group,
                        @PathVariable("id") String id,
                        HttpServletResponse response
    ) {
        List<Instance> instances = REPOSITORY.get(group);
        if (instances == null) {
            response.setStatus(NOT_FOUND.value());
            return;
        }

        for (int i = 0; i < instances.size(); i++) {
            Instance instance = instances.get(i);
            if (instance.getId().equals(id)) {
                instances.remove(i);
                return;
            }
        }

        response.setStatus(NOT_FOUND.value());
    }

    void turnOffCleaner () {
        if (cleanerExecutor != null) {
            cleanerExecutor.shutdownNow();
            cleanerExecutor = null;
        }
        expired = 0L;
    }

    void scheduleCleaner (long seconds) {
        expired = seconds;
        cleanerExecutor = Executors.newSingleThreadScheduledExecutor();

        Runnable task = () -> {
            ZonedDateTime time = ZonedDateTime.now()
                    .minus(seconds, ChronoUnit.SECONDS);

            REPOSITORY.values().stream().forEach(list ->
                    list.removeIf(it -> {
                        ZonedDateTime modified = it.getModified();
                        return modified.isBefore(time) || modified.isEqual(time);
                    })
            );
        };

        cleanerExecutor.scheduleAtFixedRate(task, seconds, seconds, SECONDS);
    }

    void load (Map<String, List<Instance>> services) {
        REPOSITORY.putAll(services);
    }

    void clear () {
        REPOSITORY.clear();
    }

    Instance find (String group, String id) {
        if (!REPOSITORY.containsKey(group)) {
            return null;
        }
        return REPOSITORY.get(group).stream()
                .filter(it -> id.equals(it.getId()))
                .findAny()
                .orElse(null);
    }

    @Configuration
    public static class SerializationConfiguration {

        @Autowired
        private ObjectMapper objectMapper;

        @PostConstruct
        public void postConstruct () {
            SimpleModule module = new SimpleModule();
            module.addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer());
            module.addSerializer(Optional.class, new OptionalSerializer());
            objectMapper.registerModule(module);
        }

        private class ZonedDateTimeSerializer extends StdSerializer<ZonedDateTime> {

            private static final long serialVersionUID = -2975123059373731680L;

            private ZonedDateTimeSerializer (Class<ZonedDateTime> t) {
                super(t);
            }

            private ZonedDateTimeSerializer () {
                this(null);
            }

            @Override
            public void serialize (ZonedDateTime value, JsonGenerator gen, SerializerProvider provider)
                    throws IOException {
                if (value == null) {
                    gen.writeNull();
                    return;
                }
                String formatted = value.format(ISO_OFFSET_DATE_TIME);
                gen.writeString(formatted);
            }
        }

        private class OptionalSerializer extends StdSerializer<Optional> {

            private OptionalSerializer (Class<Optional> t) {
                super(t);
            }

            private OptionalSerializer () {
                this(null);
            }

            @Override
            public void serialize (Optional value, JsonGenerator gen, SerializerProvider provider)
                    throws IOException {
                if (value != null && value.isPresent()) {
                    gen.writeObject(value.get());
                } else {
                    gen.writeNull();
                }
            }
        }
    }
}
