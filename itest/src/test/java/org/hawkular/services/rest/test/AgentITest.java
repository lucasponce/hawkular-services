/*
 * Copyright 2016-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.services.rest.test;

import org.hawkular.cmdgw.ws.test.EchoCommandITest;
import org.hawkular.services.rest.test.TestClient.Retry;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.logging.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Hawkular Agent integration tests.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class AgentITest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(AgentITest.class);
    /** Agent does not use tenant, just use a dummy tenant for interactions with alerts */
    private static final String testTenantId = "hawkular";
    /** The {@code feedId} used by the Agent we test */
    private static final String testFeedId = System.getProperty("hawkular.itest.rest.feedId");

    /**
     * Checks that at least the local WildFly and operating system were inserted to Inventory by Hawkular Agent.
     * <p>
     * A note about {@link Test#dependsOnGroups()}: we want these tests to run at the very end of the suite so that it
     * takes less to wait for the resources to appear in Inventory.
     *
     * @throws Throwable
     */
    @Test(dependsOnGroups = { EchoCommandITest.GROUP, AlertingITest.GROUP })
    @RunAsClient
    public void agentDiscoverySuccess() throws Throwable {
        final String wfServerId = testFeedId + "~Local~~";
        testClient.newRequest()
                .path(inventoryPath + "/resources/" + wfServerId)
                .get()
                .assertWithRetries(testResponse -> {
                    printAllResources(testFeedId, "WF Server and OS should be in inventory");
                    testResponse
                            .assertCode(200)
                            .assertJson(foundResources -> {
                                log.tracef("Got resources [%s] looking for [%s]", foundResources, wfServerId);
                                Assert.assertTrue(foundResources.isObject(), String.format(
                                        "[%s] should have returned a json object, while it returned [%s]",
                                        testResponse.getRequest(), foundResources));
                                Assert.assertTrue(dequote(foundResources.get("id").toString()).equals(wfServerId),
                                        String.format(
                                                "[%s] should have a resource with an id of [%s]",
                                                testResponse.getRequest(), wfServerId));
                                Assert.assertTrue(dequote(foundResources.get("config").get("Server State").toString())
                                        .equals("reload-required"),
                                        String.format(
                                                "[%s] should have a config 'Server State' with value 'running'",
                                                foundResources.get("config")));
                            });

                }, Retry.times(30).delay(1000));

        final String osIdEncoded = testFeedId + "~Local JMX~org.hawkular.agent:subtype=operatingsystem,type=platform";
        final String osId = testFeedId + "~Local JMX~org.hawkular.agent:subtype=operatingsystem,type=platform";
        testClient.newRequest()
                .path(inventoryPath + "/resources/" + osIdEncoded)
                .get()
                .assertWithRetries(testResponse -> {
                    printAllResources(testFeedId, "WF Server and OS should be in inventory");
                    testResponse
                            .assertCode(200)
                            .assertJson(foundResources -> {
                                log.tracef("Got resources [%s] looking for [%s]", foundResources, osId);
                                Assert.assertTrue(foundResources.isObject(), String.format(
                                        "[%s] should have returned a json object, while it returned [%s]",
                                        testResponse.getRequest(), foundResources));
                                Assert.assertTrue(dequote(foundResources.get("id").toString()).equals(osId),
                                        String.format(
                                                "[%s] should have a resource with an id of [%s]",
                                                foundResources, osId));
                            });

                }, Retry.times(30).delay(1000));
    }

    /**
     * Checks that the local WildFly discovery generated a notification.
     *
     * @throws Throwable
     */
    // TODO [lponce] Enable and adapt this test when agent is sync-ed with new inventory
    // @Test(dependsOnMethods = { "agentDiscoverySuccess" })
    // @RunAsClient
    public void agentNotificationSuccess() throws Throwable {
        StringBuffer sb = new StringBuffer("/hawkular/alerts/events");
        sb.append("?");
        sb.append("tagQuery=miq.event_type%20%3D%20hawkular_event%20AND%20miq.resource_type%20%3D%20MiddlewareServer");
        final String eventsPath = sb.toString();

        testClient.newRequest()
                .header("Hawkular-Tenant", testTenantId)
                .path(eventsPath)
                .get()
                .assertWithRetries(testResponse -> {
                    testResponse
                            .assertCode(200)
                            .assertJson(foundEvents -> {

                                log.warnf("Got events [%s]", foundEvents);
                                Assert.assertTrue(foundEvents.isArray(), String.format(
                                        "[%s] should have returned a json array, while it returned [%s]",
                                        testResponse.getRequest(), foundEvents));
                                Assert.assertTrue(foundEvents.size() == 1, String.format(
                                        "[%s] should have returned a json array with size == 1, while it returned [%s]",
                                        testResponse.getRequest(), foundEvents));
                            });

                }, Retry.times(500).delay(1000));
    }
}
