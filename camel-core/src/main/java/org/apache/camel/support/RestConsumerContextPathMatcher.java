/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.support;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * A context path matcher when using rest-dsl that allows components to reuse the same matching logic.
 * <p/>
 * The component should use the {@link #matchBestPath(String, String, java.util.List)} with the request details
 * and the matcher returns the best matched, or <tt>null</tt> if none could be determined.
 * <p/>
 * The {@link ConsumerPath} is used for the components to provide the details to the matcher.
 */
public final class RestConsumerContextPathMatcher {

    /**
     * Consumer path details which must be implemented and provided by the components.
     */
    public interface ConsumerPath<T> {

        /**
         * Any HTTP restrict method that would not be allowed
         */
        String getRestrictMethod();

        /**
         * The consumer context-path which may include wildcards
         */
        String getConsumerPath();

        /**
         * The consumer implementation
         */
        T getConsumer();

    }

    /**
     * Finds the best matching of the list of consumer paths that should service the incoming request.
     *
     * @param requestMethod   the incoming request HTTP method
     * @param requestPath     the incoming request context path
     * @param consumerPaths   the list of consumer context path details
     * @return the best matched consumer, or <tt>null</tt> if none could be determined.
     */
    public static ConsumerPath matchBestPath(String requestMethod, String requestPath, List<ConsumerPath> consumerPaths) {
        ConsumerPath answer = null;

        List<ConsumerPath> candidates = new ArrayList<ConsumerPath>();

        // first match by http method
        for (ConsumerPath entry : consumerPaths) {
            if (matchRestMethod(requestMethod, entry.getRestrictMethod())) {
                candidates.add(entry);
            }
        }

        // then see if we got a direct match
        Iterator<ConsumerPath> it = candidates.iterator();
        while (it.hasNext()) {
            ConsumerPath consumer = it.next();
            if (matchRestPath(requestPath, consumer.getConsumerPath(), false)) {
                answer = consumer;
                break;
            }
        }

        // then match by wildcard path
        if (answer == null) {
            it = candidates.iterator();
            while (it.hasNext()) {
                ConsumerPath consumer = it.next();
                // filter non matching paths
                if (!matchRestPath(requestPath, consumer.getConsumerPath(), true)) {
                    it.remove();
                }
            }

            // if there is multiple candidates with wildcards then pick anyone with the least number of wildcards
            int bestWildcard = Integer.MAX_VALUE;
            ConsumerPath best = null;
            if (candidates.size() > 1) {
                it = candidates.iterator();
                while (it.hasNext()) {
                    ConsumerPath entry = it.next();
                    int wildcards = countWildcards(entry.getConsumerPath());
                    if (wildcards > 0) {
                        if (best == null || wildcards < bestWildcard) {
                            best = entry;
                            bestWildcard = wildcards;
                        }
                    }
                }

                if (best != null) {
                    // pick the best among the wildcards
                    answer = best;
                }
            }

            // if there is one left then its our answer
            if (answer == null && candidates.size() == 1) {
                answer = candidates.get(0);
            }
        }

        return answer;
    }

    /**
     * Matches the given request HTTP method with the configured HTTP method of the consumer
     *
     * @param method    the request HTTP method
     * @param restrict  the consumer configured HTTP restrict method
     * @return <tt>true</tt> if matched, <tt>false</tt> otherwise
     */
    private static boolean matchRestMethod(String method, String restrict) {
        if (restrict == null) {
            return true;
        }

        // always match OPTIONS as some REST clients uses that prior to calling the service
        if ("OPTIONS".equals(method)) {
            return true;
        }

        return restrict.toLowerCase(Locale.US).contains(method.toLowerCase(Locale.US));
    }

    /**
     * Matches the given request path with the configured consumer path
     *
     * @param requestPath   the request path
     * @param consumerPath  the consumer path which may use { } tokens
     * @return <tt>true</tt> if matched, <tt>false</tt> otherwise
     */
    private static boolean matchRestPath(String requestPath, String consumerPath, boolean wildcard) {
        // remove starting/ending slashes
        if (requestPath.startsWith("/")) {
            requestPath = requestPath.substring(1);
        }
        if (requestPath.endsWith("/")) {
            requestPath = requestPath.substring(0, requestPath.length() - 1);
        }
        // remove starting/ending slashes
        if (consumerPath.startsWith("/")) {
            consumerPath = consumerPath.substring(1);
        }
        if (consumerPath.endsWith("/")) {
            consumerPath = consumerPath.substring(0, consumerPath.length() - 1);
        }

        // split using single char / is optimized in the jdk
        String[] requestPaths = requestPath.split("/");
        String[] consumerPaths = consumerPath.split("/");

        // must be same number of path's
        if (requestPaths.length != consumerPaths.length) {
            return false;
        }

        for (int i = 0; i < requestPaths.length; i++) {
            String p1 = requestPaths[i];
            String p2 = consumerPaths[i];

            if (wildcard && p2.startsWith("{") && p2.endsWith("}")) {
                // always matches
                continue;
            }

            if (!p1.equals(p2)) {
                return false;
            }
        }

        // assume matching
        return true;
    }

    /**
     * Counts the number of wildcards in the path
     *
     * @param consumerPath  the consumer path which may use { } tokens
     * @return number of wildcards, or <tt>0</tt> if no wildcards
     */
    private static int countWildcards(String consumerPath) {
        int wildcards = 0;

        // remove starting/ending slashes
        if (consumerPath.startsWith("/")) {
            consumerPath = consumerPath.substring(1);
        }
        if (consumerPath.endsWith("/")) {
            consumerPath = consumerPath.substring(0, consumerPath.length() - 1);
        }

        String[] consumerPaths = consumerPath.split("/");
        for (String p2 : consumerPaths) {
            if (p2.startsWith("{") && p2.endsWith("}")) {
                wildcards++;
            }
        }

        return wildcards;
    }

}