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

/**
 * Runtime discovery exception. All exception should be wrapped in it.
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since Jul 25, 2017
 */
public class DiscoveryException extends RuntimeException {

    private static final long serialVersionUID = 7127733515194769696L;

    public DiscoveryException (String message) {
        super(message);
    }
}
