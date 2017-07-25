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

import static java.util.concurrent.TimeUnit.SECONDS;
import static lombok.AccessLevel.PACKAGE;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.RetryableException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import lombok.Getter;
import lombok.Value;

/**
 * Client for working with Luntic discovery service.
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since Jul 24, 2017
 */
public class Discovery {

    /**
     * Creates client instance via {@link DiscoveryBuilder} instance.
     *
     * @return {@link DiscoveryBuilder} instance for further client building.
     */
    public static DiscoveryBuilder create () {
        return new DiscoveryBuilder();
    }

    private final Api api;

    @Getter
    private final String group;

    @Getter(PACKAGE)
    private String id;

    @Getter
    private Map<String, Object> meta;

    private ScheduledExecutorService heartbeatExecutor;

    private Discovery (Api api, String group, Map<String, Object> meta) {
        this.api = api;
        this.group = group;
        this.meta = meta;
    }

    /**
     * Returns client's instance from Luntic.
     *
     * @return current client instance.
     */
    public Instance me () {
        return api.getByGroupAndId(group, id);
    }

    /**
     * Returns list of all instances in specified group.
     *
     * @param group requested instance group name
     *
     * @return list of instances.
     */
    public List<Instance> group (String group) {
        return api.getByGroup(group);
    }

    /**
     * Returns instance by its group and id.
     *
     * @param group requested instance group name
     * @param id    requested instance id
     *
     * @return requested instance
     */
    public Instance instance (String group, String id) {
        return api.getByGroupAndId(group, id);
    }

    /**
     * Returns list of all instances of this client's group.
     *
     * @return list of instances.
     */
    public List<Instance> group () {
        return group(group);
    }

    /**
     * Returns absolutely all instances in discovery service.
     *
     * @return map group->instances
     */
    public Map<String, List<Instance>> all () {
        return api.getAll();
    }

    /**
     * Updates last modified time and meta data of client's instance.
     *
     * @param meta new meta data
     *
     * @return updated instance
     */
    public Instance update (Map<String, Object> meta) {
        this.meta = meta;
        return api.update(group, id, meta);
    }

    /**
     * Updates last modified time of client's instance.
     *
     * @return updated instance
     */
    public Instance update () {
        return api.update(group, id);
    }

    /**
     * Deregisters client from Luntic discovery service.
     */
    public void deregister () {
        api.delete(group, id);
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
        if (meta != null) {
            meta.clear();
            meta = null;
        }
    }

    private void register () {
        Response response;
        try {
            response = meta != null
                       ? api.create(group, meta)
                       : api.create(group);
        } catch (RetryableException ex) {
            throw new DiscoveryException("Couldn't register to discovery service. Reason: " + ex.getMessage());
        }

        if (response.status() != 201) {
            String message = String.format("Couldn't register to discovery service. Status: %d, reason: %s",
                                           response.status(), response.reason());
            throw new DiscoveryException(message);
        }

        String location = response.headers().entrySet().stream()
                .filter(it -> it.getKey().equalsIgnoreCase("Location"))
                .map(Entry::getValue)
                .flatMap(Collection::stream)
                .findAny()
                .orElseThrow(() -> new DiscoveryException("There is no 'Location' header"));

        id = location.substring(location.lastIndexOf('/') + 1);

        long expire = response.headers().entrySet().stream()
                .filter(it -> it.getKey().equalsIgnoreCase("X-Expired-Time"))
                .map(Entry::getValue)
                .flatMap(Collection::stream)
                .map(Integer::parseInt)
                .findAny()
                .orElse(0) - 1;

        if (expire > 0) {
            if (heartbeatExecutor == null) {
                heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
            }
            heartbeatExecutor.scheduleAtFixedRate(
                    new HeartbeatTask(),
                    0,
                    expire,
                    SECONDS
            );
        }
    }

    /**
     *
     */
    public static class DiscoveryBuilder {

        private String url;

        private String group = "default";

        private Map<String, Object> meta;

        /**
         * Sets url for client.
         * It is primary parameter, without default value.
         * <p/>
         * Url should follow the next format:
         * <p/>
         * {@code [http://]hostname[:port][/pathPrefix] }
         *
         * @param url client's url
         *
         * @return builder for further client creating
         */
        public DiscoveryBuilder url (String url) {
            this.url = url;
            return this;
        }

        /**
         * Sets client's group name.
         * It is optional parameter, default value - "default"
         *
         * @param group client's group name
         *
         * @return builder for further client creating
         */
        public DiscoveryBuilder group (String group) {
            this.group = group;
            return this;
        }

        /**
         * Sets initial client's meta data for registration.
         * It is optional parameter, default value - {@code null}
         *
         * @param meta map of initial meta data
         *
         * @return builder for further client creating
         */
        public DiscoveryBuilder meta (Map<String, Object> meta) {
            this.meta = meta;
            return this;
        }

        /**
         * Creates client instance and registers it.
         * If Luntic was started with heartbeat mode - the specific heartbeat task starts in separate scheduled thread.
         *
         * @return new registered discovery client
         */
        public Discovery register () {
            ObjectMapper mapper = new ObjectMapper();
            Api api = Api.connect(url, mapper);
            Discovery discovery = new Discovery(api, group, meta);
            discovery.register();
            return discovery;
        }
    }

    @Value
    private final class HeartbeatTask implements Runnable {

        @Override
        public void run () {
            api.update(group, id);
        }
    }
}
