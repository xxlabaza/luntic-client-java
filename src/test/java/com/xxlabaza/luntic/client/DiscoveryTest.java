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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since Jul 25, 2017
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = DEFINED_PORT,
        classes = Server.class
)
public class DiscoveryTest {

    @Autowired
    private Server server;

    @After
    public void after () {
        server.clear();
    }

    @Test
    public void registration () {
        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group("popa")
                .register();

        assertNotNull(discovery);
        assertEquals("popa", discovery.getGroup());

        Instance instance = server.find(discovery.getGroup(), discovery.getId());
        assertNotNull(instance);
        assertFalse(instance.getMeta().isPresent());
    }

    @Test
    public void heartbeat () throws InterruptedException {
        loadList("popa", 2);

        server.scheduleCleaner(2);

        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group("popa")
                .register();

        List<Instance> group = discovery.group();
        assertEquals(3, group.size());

        SECONDS.sleep(2);

        group = discovery.group();
        assertEquals(1, group.size());
        assertEquals(discovery.getId(), group.get(0).getId());

        MILLISECONDS.sleep(2500);

        group = discovery.group();
        assertEquals(1, group.size());
        assertEquals(discovery.getId(), group.get(0).getId());

        server.turnOffCleaner();
    }

    @Test
    public void registrationWithMeta () {
        Map<String, Object> expected = new HashMap<>(4, 1.F);
        expected.put("number", 1);
        expected.put("boolean", true);
        expected.put("double", 1.5D);
        expected.put("string", "Hello world");

        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group("popa")
                .meta(expected)
                .register();

        assertNotNull(discovery);
        assertEquals("popa", discovery.getGroup());

        Instance instance = server.find(discovery.getGroup(), discovery.getId());
        assertNotNull(instance);
        assertMeta(expected, instance);
    }

    @Test
    public void getUnknown () {
        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group("popa")
                .register();

        assertTrue(discovery.group("unknown").isEmpty());
        assertNull(discovery.instance("unknown", "123"));
        assertNull(discovery.instance("popa", "123"));
    }

    @Test
    public void getMe () {
        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group("popa")
                .register();

        Instance instance = discovery.me();
        assertNotNull(instance);

        assertNotNull(instance.getId());
        assertEquals("popa", instance.getGroup());
        assertNotNull(instance.getCreated());
        assertNotNull(instance.getModified());
        assertFalse(instance.getMeta().isPresent());
    }

    @Test
    public void getMeWithMeta () {
        Map<String, Object> expected = new HashMap<>(4, 1.F);
        expected.put("number", 1);
        expected.put("boolean", true);
        expected.put("double", 1.5D);
        expected.put("string", "Hello world");

        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group("popa")
                .meta(expected)
                .register();

        Instance instance = discovery.me();
        assertNotNull(instance);

        assertNotNull(instance.getId());
        assertEquals("popa", instance.getGroup());
        assertNotNull(instance.getCreated());
        assertNotNull(instance.getModified());
        assertMeta(expected, instance);
    }

    @Test
    public void getOwnGroup () {
        String group = "popa";
        loadList(group, 4);

        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group(group)
                .register();

        List<Instance> result = discovery.group();
        assertNotNull(result);
        assertEquals(5, result.size());

        assertTrue(result.stream().map(Instance::getGroup).allMatch(group::equals));
    }

    @Test
    public void getAll () {
        String groupName1 = "one";
        loadList(groupName1, 3);

        String groupName2 = "two";
        loadList(groupName2, 2);

        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group("popa")
                .register();

        Map<String, List<Instance>> result = discovery.all();
        assertNotNull(result);
        assertEquals(3, result.size());

        List<Instance> group1 = result.get(groupName1);
        assertEquals(3, group1.size());
        assertTrue(group1.stream().map(Instance::getGroup).allMatch(groupName1::equals));

        List<Instance> group2 = result.get(groupName2);
        assertEquals(2, group2.size());
        assertTrue(group2.stream().map(Instance::getGroup).allMatch(groupName2::equals));

        List<Instance> group3 = result.get("popa");
        assertEquals(1, group3.size());
        assertEquals("popa", group3.get(0).getGroup());
    }

    @Test
    public void updateModifiedTime () {
        Map<String, Object> meta = new HashMap<>(4, 1.F);
        meta.put("number", 1);
        meta.put("boolean", true);
        meta.put("double", 1.5D);
        meta.put("string", "Hello world");

        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group("popa")
                .meta(meta)
                .register();

        Instance instance1 = discovery.me();
        Instance instance2 = discovery.update();

        assertEquals(instance1.getId(), instance2.getId());
        assertEquals(instance1.getGroup(), instance2.getGroup());
        assertEquals(instance1.getCreated(), instance2.getCreated());
        assertNotEquals(instance1.getModified(), instance2.getModified());
        assertEquals(instance1.getMeta(), instance2.getMeta());
    }

    @Test
    public void updateMetadata () {
        Map<String, Object> metaInitial = new HashMap<>(4, 1.F);
        metaInitial.put("number", 1);
        metaInitial.put("boolean", true);
        metaInitial.put("double", 1.5D);
        metaInitial.put("string", "Hello world");

        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group("popa")
                .meta(metaInitial)
                .register();

        Instance instance1 = discovery.me();

        Map<String, Object> metaNew = new HashMap<>(4, 1.F);
        metaNew.put("number", 42);
        metaNew.put("boolean", false);
        metaNew.put("double", 0.D);
        metaNew.put("string", "Bye");

        Instance instance2 = discovery.update(metaNew);

        assertEquals(instance1.getId(), instance2.getId());
        assertNotEquals(instance1.getModified(), instance2.getModified());
        assertNotEquals(instance1.getMeta(), instance2.getMeta());
        assertEquals(metaInitial, instance1.getMeta().get());
        assertEquals(metaNew, instance2.getMeta().get());
    }

    @Test
    public void deregister () {
        Discovery discovery = Discovery.create()
                .url("localhost:8080")
                .group("popa")
                .register();

        assertNotNull(server.find(discovery.getGroup(), discovery.getId()));

        discovery.deregister();

        assertNull(server.find(discovery.getGroup(), discovery.getId()));
    }

    private void assertMeta (Map<String, Object> expected, Instance instance) {
        assertTrue(instance.getMeta().isPresent());

        Map<String, Object> meta = instance.getMeta().get();
        assertEquals(expected.size(), meta.size());

        expected.entrySet().stream().forEach(it -> {
            assertTrue(meta.containsKey(it.getKey()));
            assertEquals(it.getValue(), meta.get(it.getKey()));
        });
    }

    private void loadList (String group, int count) {
        ZonedDateTime zdt = ZonedDateTime.now();
        List<Instance> list = IntStream.range(0, count).boxed()
                .map(it -> Instance.builder()
                        .id(UUID.randomUUID().toString())
                        .group(group)
                        .created(zdt)
                        .modified(zdt)
                        .build())
                .collect(toList());

        Map<String, List<Instance>> map = new HashMap<>(1, 1.F);
        map.put(group, list);

        server.load(map);
    }
}
