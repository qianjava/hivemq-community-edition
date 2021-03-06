/*
 * Copyright 2019 dc-square GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hivemq.extensions.services.subscription;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.packets.general.Qos;
import com.hivemq.extension.sdk.api.services.exception.DoNotImplementException;
import com.hivemq.extension.sdk.api.services.exception.InvalidTopicException;
import com.hivemq.extension.sdk.api.services.exception.NoSuchClientIdException;
import com.hivemq.extension.sdk.api.services.subscription.SubscriptionStore;
import com.hivemq.extension.sdk.api.services.subscription.TopicSubscription;
import com.hivemq.extensions.services.PluginServiceRateLimitService;
import com.hivemq.mqtt.message.QoS;
import com.hivemq.mqtt.message.mqtt5.Mqtt5RetainHandling;
import com.hivemq.mqtt.message.subscribe.Topic;
import com.hivemq.persistence.clientsession.ClientSessionSubscriptionPersistence;
import com.hivemq.persistence.clientsession.callback.SubscriptionResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import util.TestException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Florian Limpöck
 * @since 4.0.0
 */
@SuppressWarnings("NullabilityAnnotations")
public class SubscriptionStoreImplTest {

    private SubscriptionStore subscriptionStore;

    @Mock
    private ClientSessionSubscriptionPersistence clientSessionSubscriptionPersistence;

    @Mock
    private PluginServiceRateLimitService rateLimitService;

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);
        subscriptionStore = new SubscriptionStoreImpl(clientSessionSubscriptionPersistence, rateLimitService);
        when(rateLimitService.rateLimitExceeded()).thenReturn(false);
    }

    @Test(timeout = 10000)
    public void test_get_rate_limit_exceeded() {

        when(rateLimitService.rateLimitExceeded()).thenReturn(true);

        subscriptionStore.getSubscriptions("client");

        verify(clientSessionSubscriptionPersistence, never()).getSubscriptions("client");

    }

    @Test(timeout = 10000)
    public void test_add_rate_limit_exceeded() {

        when(rateLimitService.rateLimitExceeded()).thenReturn(true);

        subscriptionStore.addSubscription("client", new TopicSubscriptionImpl("topic", Qos.AT_MOST_ONCE, false, false, 0));

        verify(clientSessionSubscriptionPersistence, never()).addSubscription(eq("client"), any(Topic.class));

    }

    @Test(timeout = 10000)
    public void test_add_multi_rate_limit_exceeded() {

        when(rateLimitService.rateLimitExceeded()).thenReturn(true);

        subscriptionStore.addSubscriptions("client", ImmutableSet.of(new TopicSubscriptionImpl("topic", Qos.AT_MOST_ONCE, false, false, 0)));

        verify(clientSessionSubscriptionPersistence, never()).addSubscriptions(eq("client"), any(ImmutableSet.class));

    }

    @Test(timeout = 10000)
    public void test_remove_rate_limit_exceeded() {

        when(rateLimitService.rateLimitExceeded()).thenReturn(true);

        subscriptionStore.removeSubscription("client", "topic");

        verify(clientSessionSubscriptionPersistence, never()).remove("client", "topic");

    }

    @Test(timeout = 10000)
    public void test_remove_multi_rate_limit_exceeded() {

        when(rateLimitService.rateLimitExceeded()).thenReturn(true);

        subscriptionStore.removeSubscriptions("client", Sets.newHashSet("topic"));

        verify(clientSessionSubscriptionPersistence, never()).removeSubscriptions(anyString(), any(ImmutableSet.class));

    }

    @Test(expected = NullPointerException.class, timeout = 10000)
    public void test_get_null() throws Throwable {

        try {
            subscriptionStore.getSubscriptions(null).get();
        } catch (final Exception e) {
            throw e.getCause();
        }

        verify(clientSessionSubscriptionPersistence, never()).getSubscriptions("client");

    }

    @Test(expected = NullPointerException.class, timeout = 10000)
    public void test_add_null_client_id() throws Throwable {

        try {
            subscriptionStore.addSubscription(null, new TopicSubscriptionImpl("topic", Qos.AT_MOST_ONCE, false, false, 0)).get();
        } catch (final Exception e) {
            throw e.getCause();
        }

        verify(clientSessionSubscriptionPersistence, never()).addSubscription(eq("client"), any(Topic.class));

    }

    @Test(expected = NullPointerException.class, timeout = 10000)
    public void test_add_null_topic() throws Throwable {

        try {
            subscriptionStore.addSubscription("client", null).get();
        } catch (final Exception e) {
            throw e.getCause();
        }

        verify(clientSessionSubscriptionPersistence, never()).addSubscription(eq("client"), any(Topic.class));

    }

    @Test(expected = NullPointerException.class, timeout = 10000)
    public void test_add_multi_null_client_id() throws Throwable {

        try {
            subscriptionStore.addSubscriptions(null, ImmutableSet.of(new TopicSubscriptionImpl("topic", Qos.AT_MOST_ONCE, false, false, 0))).get();
        } catch (final Exception e) {
            throw e.getCause();
        }

        verify(clientSessionSubscriptionPersistence, never()).addSubscriptions(anyString(), any(ImmutableSet.class));

    }

    @Test(expected = NullPointerException.class, timeout = 10000)
    public void test_add_multi_null_topics() throws Throwable {

        subscriptionStore.addSubscriptions("client", null);

        verify(clientSessionSubscriptionPersistence, never()).addSubscriptions(eq("client"), any(ImmutableSet.class));

    }

    @Test(expected = IllegalArgumentException.class, timeout = 10000)
    public void test_add_multi_empty_topics() throws Throwable {

        subscriptionStore.addSubscriptions("client", ImmutableSet.of());

        verify(clientSessionSubscriptionPersistence, never()).addSubscriptions(eq("client"), any(ImmutableSet.class));

    }

    @Test(expected = NullPointerException.class, timeout = 10000)
    public void test_remove_null_client_id() throws Throwable {

        try {
            subscriptionStore.removeSubscription(null, "topic").get();
        } catch (final Exception e) {
            throw e.getCause();
        }

        verify(clientSessionSubscriptionPersistence, never()).remove("client", "topic");

    }

    @Test(expected = NullPointerException.class, timeout = 10000)
    public void test_remove_null_topic() throws Throwable {

        try {
            subscriptionStore.removeSubscription("client", null).get();
        } catch (final Exception e) {
            throw e.getCause();
        }

        verify(clientSessionSubscriptionPersistence, never()).remove("client", "topic");

    }

    @Test(expected = NullPointerException.class, timeout = 10000)
    public void test_remove_multi_null_client_id() throws Throwable {

        subscriptionStore.removeSubscriptions(null, ImmutableSet.of("topic"));

        verify(clientSessionSubscriptionPersistence, never()).removeSubscriptions(anyString(), any(ImmutableSet.class));

    }

    @Test(expected = NullPointerException.class, timeout = 10000)
    public void test_remove_multi_null_topic() throws Throwable {

        subscriptionStore.removeSubscriptions("client", null);

        verify(clientSessionSubscriptionPersistence, never()).removeSubscriptions(anyString(), any(ImmutableSet.class));

    }

    @Test(expected = IllegalArgumentException.class, timeout = 10000)
    public void test_remove_multi_empty_topics() throws Throwable {

        subscriptionStore.removeSubscriptions("client", ImmutableSet.of());

        verify(clientSessionSubscriptionPersistence, never()).removeSubscriptions(anyString(), any(ImmutableSet.class));

    }

    @Test(timeout = 10_000)
    public void test_get_success() throws ExecutionException, InterruptedException {

        final Topic topic = new Topic("topic", QoS.AT_LEAST_ONCE, true,
                true, Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);

        when(clientSessionSubscriptionPersistence.getSubscriptions("client")).thenReturn(ImmutableSet.of(topic));

        final Set<TopicSubscription> subscriptions = subscriptionStore.getSubscriptions("client").get();

        assertEquals(1, subscriptions.size());

        verify(clientSessionSubscriptionPersistence).getSubscriptions("client");

    }

    @Test(timeout = 10_000, expected = UnsupportedOperationException.class)
    public void test_get_success_unmodifiable() throws ExecutionException, InterruptedException {

        final Topic topic = new Topic("topic", QoS.AT_LEAST_ONCE, true, true,
                Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);

        when(clientSessionSubscriptionPersistence.getSubscriptions("client")).thenReturn(ImmutableSet.of(topic));

        final Set<TopicSubscription> subscriptions = subscriptionStore.getSubscriptions("client").get();

        assertEquals(1, subscriptions.size());

        verify(clientSessionSubscriptionPersistence).getSubscriptions("client");

        subscriptions.add(new TopicSubscriptionImpl(topic));

    }

    @Test(timeout = 10_000)
    public void test_add_success() throws ExecutionException, InterruptedException {

        final Topic topic = new Topic("topic", QoS.AT_LEAST_ONCE, true,
                true, Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);

        when(clientSessionSubscriptionPersistence.addSubscription("client", topic)).thenReturn(
                Futures.immediateFuture(new SubscriptionResult(topic, false, null)));

        subscriptionStore.addSubscription("client", new TopicSubscriptionImpl(topic)).get();

        verify(clientSessionSubscriptionPersistence).addSubscription(eq("client"), any(Topic.class));

    }

    @Test(timeout = 10_000)
    public void test_add_multi_success() throws ExecutionException, InterruptedException {

        final Topic topic1 = new Topic("topic1", QoS.AT_LEAST_ONCE, true,
                true, Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);
        final Topic topic2 = new Topic("topic2", QoS.AT_LEAST_ONCE, true,
                true, Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);

        when(clientSessionSubscriptionPersistence.addSubscriptions("client", ImmutableSet.of(topic1, topic2))).thenReturn(
                Futures.immediateFuture(ImmutableList.of()));

        subscriptionStore.addSubscriptions("client", ImmutableSet.of(new TopicSubscriptionImpl(topic1), new TopicSubscriptionImpl(topic2))).get();

        verify(clientSessionSubscriptionPersistence).addSubscriptions(eq("client"), any(ImmutableSet.class));

    }

    @Test(timeout = 10_000, expected = NullPointerException.class)
    public void test_add_multi_one_null() throws ExecutionException, InterruptedException {

        final Topic topic1 = new Topic("topic1", QoS.AT_LEAST_ONCE, true,
                true, Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);

        final Set<TopicSubscription> set = new HashSet<>();

        set.add(new TopicSubscriptionImpl(topic1));
        set.add(null);

        subscriptionStore.addSubscriptions("client", set).get();

    }

    @Test(timeout = 10_000, expected = NullPointerException.class)
    public void test_remove_multi_one_null() throws ExecutionException, InterruptedException {

        final Set<String> set = new HashSet<>();

        set.add("topic1");
        set.add(null);

        subscriptionStore.removeSubscriptions("client", set).get();

    }

    @Test(timeout = 10_000, expected = NoSuchClientIdException.class)
    public void test_add_failed_client_session_not_existent() throws Throwable {

        final Topic topic = new Topic("topic", QoS.AT_LEAST_ONCE, true, true,
                Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);

        when(clientSessionSubscriptionPersistence.addSubscription("client", topic)).thenReturn(
                Futures.immediateFuture(null));

        try {
            subscriptionStore.addSubscription("client", new TopicSubscriptionImpl(topic)).get();
        } catch (final Throwable t) {
            throw t.getCause();
        }
    }

    @Test(timeout = 10_000, expected = NoSuchClientIdException.class)
    public void test_add_multi_failed_client_session_not_existent() throws Throwable {

        final Topic topic = new Topic("topic", QoS.AT_LEAST_ONCE, true, true,
                Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);

        when(clientSessionSubscriptionPersistence.addSubscriptions("client", ImmutableSet.of(topic))).thenReturn(
                Futures.immediateFuture(null));

        try {
            subscriptionStore.addSubscriptions("client", ImmutableSet.of(new TopicSubscriptionImpl(topic))).get();
        } catch (final Throwable t) {
            throw t.getCause();
        }
    }

    @Test(timeout = 10_000, expected = ExecutionException.class)
    public void test_add_failed() throws ExecutionException, InterruptedException {

        final Topic topic = new Topic("topic", QoS.AT_LEAST_ONCE, true, true,
                Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);

        when(clientSessionSubscriptionPersistence.addSubscription("client", topic)).thenReturn(
                Futures.immediateFailedFuture(TestException.INSTANCE));

        subscriptionStore.addSubscription("client", new TopicSubscriptionImpl(topic)).get();

        verify(clientSessionSubscriptionPersistence).addSubscription(eq("client"), any(Topic.class));

    }

    @Test(timeout = 10_000, expected = ExecutionException.class)
    public void test_add_multi_failed() throws ExecutionException, InterruptedException {

        final Topic topic = new Topic("topic", QoS.AT_LEAST_ONCE, true, true,
                Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);

        when(clientSessionSubscriptionPersistence.addSubscriptions("client", ImmutableSet.of(topic))).thenReturn(
                Futures.immediateFailedFuture(TestException.INSTANCE));

        subscriptionStore.addSubscriptions("client", ImmutableSet.of(new TopicSubscriptionImpl(topic))).get();

        verify(clientSessionSubscriptionPersistence).addSubscriptions(eq("client"), any(ImmutableSet.class));

    }

    @Test(expected = DoNotImplementException.class, timeout = 20000)
    public void test_add_subscription_falsely_implemented_class() throws Throwable {

        try {
            subscriptionStore.addSubscription("client", new TestSubscriptionImpl()).get();
        } catch (final Throwable throwable) {
            throw throwable.getCause();
        }

    }

    @Test(expected = DoNotImplementException.class, timeout = 20000)
    public void test_add_multi_subscription_falsely_implemented_class() throws Throwable {

        final Topic topic = new Topic("topic", QoS.AT_LEAST_ONCE, true, true,
                Mqtt5RetainHandling.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST, 1);

        try {
            subscriptionStore.addSubscriptions("client", ImmutableSet.of(new TopicSubscriptionImpl(topic), new TestSubscriptionImpl())).get();
        } catch (final Throwable throwable) {
            throw throwable.getCause();
        }

    }

    @Test(timeout = 10_000)
    public void test_remove_success() throws ExecutionException, InterruptedException {

        when(clientSessionSubscriptionPersistence.remove("client", "topic")).thenReturn(Futures.immediateFuture(null));

        subscriptionStore.removeSubscription("client", "topic").get();

        verify(clientSessionSubscriptionPersistence).remove("client", "topic");

    }

    @Test(timeout = 10_000)
    public void test_remove_multi_success() throws ExecutionException, InterruptedException {

        when(clientSessionSubscriptionPersistence.removeSubscriptions("client", ImmutableSet.of("topic", "topic2"))).thenReturn(Futures.immediateFuture(null));

        subscriptionStore.removeSubscriptions("client", ImmutableSet.of("topic", "topic2")).get();

        verify(clientSessionSubscriptionPersistence).removeSubscriptions("client", ImmutableSet.of("topic", "topic2"));

    }

    @Test(timeout = 10_000, expected = InvalidTopicException.class)
    public void test_remove_failed_topic_empty() throws Throwable {
        try {
            subscriptionStore.removeSubscription("client", "").get();
        } catch (final Throwable throwable) {
            throw throwable.getCause();
        }

    }

    @Test(timeout = 10_000, expected = InvalidTopicException.class)
    public void test_remove_multi_failed_topic_empty() throws Throwable {
        try {
            subscriptionStore.removeSubscriptions("client", ImmutableSet.of("topic", "", "huhu")).get();
        } catch (final Throwable throwable) {
            throw throwable.getCause();
        }

    }

    @Test(timeout = 10_000, expected = InvalidTopicException.class)
    public void test_remove_failed_topic_bad_char() throws Throwable {
        try {
            subscriptionStore.removeSubscription("client", "123" + "\u0000").get();
        } catch (final Throwable throwable) {
            throw throwable.getCause();
        }

    }

    @Test(timeout = 10_000, expected = InvalidTopicException.class)
    public void test_remove_multi_failed_topic_bad_char() throws Throwable {
        try {
            subscriptionStore.removeSubscriptions("client", ImmutableSet.of("topic", "123" + "\u0000")).get();
        } catch (final Throwable throwable) {
            throw throwable.getCause();
        }

    }

    private static class TestSubscriptionImpl implements TopicSubscription {

        @NotNull
        @Override
        public String getTopicFilter() {
            return null;
        }

        @NotNull
        @Override
        public Qos getQos() {
            return null;
        }

        @Override
        public boolean getRetainAsPublished() {
            return false;
        }

        @Override
        public boolean getNoLocal() {
            return false;
        }

        @NotNull
        @Override
        public Optional<Integer> getSubscriptionIdentifier() {
            return Optional.empty();
        }
    }
}