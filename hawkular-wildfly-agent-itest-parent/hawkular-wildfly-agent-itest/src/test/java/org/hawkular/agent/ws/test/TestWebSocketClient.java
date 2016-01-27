/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.ws.test;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.hamcrest.TypeSafeMatcher;
import org.hawkular.bus.common.BasicMessageWithExtraData;
import org.hawkular.cmdgw.api.ApiDeserializer;
import org.hawkular.cmdgw.api.WelcomeResponse;
import org.testng.Assert;

import com.samskivert.mustache.Mustache;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocket.PayloadType;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;

import okio.Buffer;
import okio.BufferedSource;

/**
 * A client for testing WebSocket conversations. A typical usage starts by {@link TestWebSocketClient#builder()}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 */
public class TestWebSocketClient implements Closeable {
    /**
     * A matcher for messages having binary attachments.
     *
     * @see Builder#expectBinary(String, TypeSafeMatcher)
     */
    public static class BinaryAwareMatcher extends PatternMatcher {

        private TypeSafeMatcher<InputStream> binaryMatcher;

        public BinaryAwareMatcher(String pattern, TypeSafeMatcher<InputStream> binaryMatcher) {
            super(pattern);
            this.binaryMatcher = binaryMatcher;
        }

        @Override
        public void describeTo(Description description) {
            super.describeTo(description);
            description.appendDescriptionOf(binaryMatcher);
        }

        @Override
        public boolean matches(ReusableBuffer actual, TestListener testListener) {
            return super.matches(actual, testListener) && binaryMatcher.matches(actual.getBinaryPart());
        }

    }

    /**
     * @see TestWebSocketClient#builder()
     *
     */
    public static class Builder {
        private final List<ExpectedMessage> expectedMessages = new ArrayList<>();
        private Request request;

        public TestWebSocketClient build() {
            TestListener listener = new TestListener(Collections.unmodifiableList(expectedMessages));
            return new TestWebSocketClient(request, listener);
        }

        /**
         * @param textPattern a regular expression
         * @param binaryMatcher a custom matcher to check the incoming binary attachment
         * @return this builder
         */
        public Builder expectBinary(String textPattern, TypeSafeMatcher<InputStream> binaryMatcher) {
            ExpectedMessage expectedMessage =
                    new ExpectedMessage(new BinaryAwareMatcher(textPattern, binaryMatcher),
                            CoreMatchers.equalTo(PayloadType.BINARY), null, null);
            expectedMessages.add(expectedMessage);
            return this;
        }

        public Builder expectGenericSuccess(String feedId) {
            ExpectedMessage expectedMessage = new ExpectedMessage(
                    new PatternMatcher("\\QGenericSuccessResponse={\"message\":"
                            + "\"The request has been forwarded to feed ["
                            + feedId + "] (\\E.*"),
                    CoreMatchers.equalTo(PayloadType.TEXT), null, null);
            expectedMessages.add(expectedMessage);
            return this;
        }

        public Builder expectMessage(ExpectedMessage expectedMessage) {
            expectedMessages.add(expectedMessage);
            return this;
        }

        /**
         * @param expectedTextMessage a plain text message to compare (rather than a regular expression)
         * @return this builder
         */
        public Builder expectText(String expectedTextMessage) {
            ExpectedMessage expectedMessage =
                    new ExpectedMessage(new PatternMatcher("\\Q" + expectedTextMessage + "\\E.*"),
                            CoreMatchers.equalTo(PayloadType.TEXT), null, null);
            expectedMessages.add(expectedMessage);
            return this;
        }

        /**
         * @param expectedRegex a text message regular expression to match
         * @return this builder
         */
        public Builder expectRegex(String expectedRegex) {
            ExpectedMessage expectedMessage = new ExpectedMessage(new PatternMatcher(expectedRegex),
                    CoreMatchers.equalTo(PayloadType.TEXT), null, null);
            expectedMessages.add(expectedMessage);
            return this;
        }

        public Builder expectWelcome(String answer) {
            return expectWelcome(answer, null);
        }

        /**
         * @param answer the text message to send out if the welcome came as expected
         * @param attachment bits to send out as a binary attachment of {@code answer}
         * @return this builder
         */
        public Builder expectWelcome(String answer, URL attachment) {
            ExpectedMessage expectedMessage = new ExpectedMessage(new WelcomeMatcher(),
                    CoreMatchers.equalTo(PayloadType.TEXT), answer, attachment);
            expectedMessages.add(expectedMessage);
            return this;
        }

        /**
         * @param url the URL of the WebSocket endpoint
         * @return this builder
         */
        public Builder url(String url) {
            this.request = new Request.Builder().url(url).build();
            return this;
        }

    }

    /**
     * A pair of {@link Matcher}s for checking the incoming messages optionally bundled with a text and binary answer to
     * send out if the incoming message matched the expectations.
     */
    public static class ExpectedMessage {

        private final URL binaryAnswer;

        protected final WebSocketArgumentMatcher<ReusableBuffer> inMessageMatcher;
        private final org.hamcrest.Matcher<PayloadType> inTypeMatcher;
        private final String textAnswer;

        public ExpectedMessage(WebSocketArgumentMatcher<ReusableBuffer> inMessageMatcher,
                org.hamcrest.Matcher<PayloadType> inTypeMatcher, String textAnswer, URL binaryAnswer) {
            super();
            this.inMessageMatcher = inMessageMatcher;
            this.inTypeMatcher = inTypeMatcher;
            this.textAnswer = textAnswer;
            this.binaryAnswer = binaryAnswer;
        }

        public MessageReport assertMatch(TestListener testListener, int index, ReusableBuffer message,
                PayloadType type) {
            Description description = new StringDescription();
            boolean fail = false;
            if (!inMessageMatcher.matches(message, testListener)) {
                description
                        .appendText("Expected: ")
                        .appendDescriptionOf(inMessageMatcher)
                        .appendText("\n     but: ");
                inMessageMatcher.describeMismatch(message, description);
                fail = true;
            }
            if (!inTypeMatcher.matches(type)) {
                description
                        .appendText("Expected: ")
                        .appendDescriptionOf(inTypeMatcher)
                        .appendText("\n     but: ");
                inTypeMatcher.describeMismatch(type, description);
                fail = true;
            }

            if (fail) {
                return new MessageReport(new AssertionError(description.toString()), index);
            } else {
                return MessageReport.passed(index);
            }
        }

        /**
         * @return a binary attachment to send out with {@link #getTextAnswer()} if the incoming message matched the
         *         expectations
         */
        public URL getBinaryAnswer() {
            return binaryAnswer;
        }

        /**
         * @return a text message to send out if the incoming message matched the expectations
         */
        public String getTextAnswer() {
            return textAnswer;
        }

        public boolean hasAnswer() {
            return textAnswer != null || binaryAnswer != null;
        }
    }

    /**
     * Some basic data about how a given message fulfilled the expectations set in {@link ExpectedMessage}.
     */
    public static class MessageReport {
        public static MessageReport passed(int index) {
            return new MessageReport(null, index);
        }

        private final int messageIndex;
        private final Throwable throwable;

        public MessageReport(Throwable throwable, int messageIndex) {
            super();
            this.throwable = throwable;
            this.messageIndex = messageIndex;
        }

        /**
         * @return the order in which the given message came. The first index is {@code 0}
         */
        public int getMessageIndex() {
            return messageIndex;
        }

        /**
         * @return the {@link Throwable} associated with this report
         */
        public Throwable getThrowable() {
            return throwable;
        }

        /**
         * @return {@code true} if the received message matched the expectations set in {@link ExpectedMessage}.
         */
        public boolean passed() {
            return this.throwable == null;
        }

        public String toString() {
            return "Message[" + messageIndex + "]: [" + (passed() ? "PASSED" : "FAILED - " + throwable.getMessage())
                    + "]";
        }
    }

    /**
     * A regular expression matcher.
     *
     */
    public static class PatternMatcher extends TypeSafeMatcher<ReusableBuffer>
            implements WebSocketArgumentMatcher<ReusableBuffer> {
        protected final String pattern;

        /**
         * @param pattern a regular expression to match the text part of the incoming message. The pattern may contain
         *            Mustache placeholders (such as {@code sessionId}) that will be resolved against an instance of
         *            {@link TestListener}.
         */
        public PatternMatcher(String pattern) {
            super();
            this.pattern = pattern;
        }

        /**
         * Resolve the Mustache placeholders (such as {@code sessionId}) against the given instance of
         * {@link TestListener}
         *
         * @return the resolved and compiled {@link Pattern}
         */
        protected Pattern compile(TestListener testListener) {
            String resolvedPattern =
                    testListener == null ? pattern : Mustache.compiler().compile(pattern).execute(testListener);
            return Pattern.compile(resolvedPattern);
        }

        /** @see org.hamcrest.SelfDescribing#describeTo(org.hamcrest.Description) */
        @Override
        public void describeTo(Description description) {
            description.appendText("to match pattern ").appendValue(pattern);
        }

        @Override
        public boolean matches(ReusableBuffer actual, TestListener testListener) {
            return compile(testListener).matcher(actual.getTextPart()).matches();
        }

        @Override
        protected boolean matchesSafely(ReusableBuffer item) {
            /* make sure we do not circumvent matches(ReusableBuffer actual, TestListener testListener) */
            throw new UnsupportedOperationException();
        }

    }

    /**
     * A utility to manipulate {@link BufferedSource} representations of incoming messages.
     */
    public static class ReusableBuffer {
        /** The index of the first byte of the binary attachment in {@link #bytes} array */
        private final int binaryOffset;
        private final byte[] bytes;
        private boolean lastWasHighSurrogate = false;
        private final String textPart;

        public ReusableBuffer(BufferedSource payload) throws IOException {
            this.bytes = payload.readByteArray();
            payload.close();

            int binOffset = 0;
            StringBuilder sb = new StringBuilder();
            try (Reader r = new InputStreamReader(new ByteArrayInputStream(bytes), "utf-8")) {
                int numberOfOpenedObjects = 0;
                int c;
                LOOP: while ((c = r.read()) >= 0) {
                    char ch = (char) c;
                    switch (ch) {
                        case '{':
                            numberOfOpenedObjects++;
                            sb.append(ch);
                            binOffset += byteLength(ch);
                            break;
                        case '}':
                            numberOfOpenedObjects--;
                            sb.append(ch);
                            binOffset += byteLength(ch);
                            if (numberOfOpenedObjects == 0) {
                                /* at this point, we closed all json objects that we have opened, so, we are at
                                 * the end of the text part of the message */
                                break LOOP;
                            }
                            break;
                        default:
                            sb.append(ch);
                            binOffset += byteLength(ch);
                            break;
                    }
                }
                this.textPart = sb.toString();
                this.binaryOffset = binOffset;
            }

        }

        /**
         * Based on http://stackoverflow.com/a/8512877
         *
         * @param ch
         * @return
         */
        private int byteLength(char ch) {
            if (lastWasHighSurrogate) {
                lastWasHighSurrogate = false;
                return 2;
            } else if (ch <= 0x7F) {
                return 1;
            } else if (ch <= 0x7FF) {
                return 2;
            } else if (Character.isHighSurrogate(ch)) {
                lastWasHighSurrogate = true;
                return 2;
            } else {
                return 3;
            }
        }

        public Buffer copy() {
            Buffer payloadCopy = new Buffer();
            payloadCopy.write(bytes);
            return payloadCopy;
        }

        public int getBinaryLength() {
            return bytes.length - binaryOffset;
        }

        public InputStream getBinaryPart() {
            if (binaryOffset >= bytes.length) {
                throw new IllegalStateException("No binary attachment in this buffer");
            }
            return new ByteArrayInputStream(bytes, binaryOffset, bytes.length - binaryOffset);
        }

        public String getTextPart() {
            return textPart;
        }

        public boolean hasBinaryPart() {
            return binaryOffset < bytes.length;
        }

        public String toString() {
            return getTextPart()
                    + (hasBinaryPart() ? " + [" + getBinaryLength() + "] bytes of binary attachment" : "");
        }
    }

    /**
     * An implementation of {@link WebSocketListener} that does matches the {@link ExpectedMessage}s against incoming
     * messages and reports the results.
     */
    public static class TestListener implements WebSocketListener, Closeable {

        private boolean closed;

        /**
         * Actually a blocing variable rather than a queue. Used to hand over the results to a consumer that runs in
         * another thread.
         */
        private final BlockingQueue<List<MessageReport>> conversationResult;

        private final List<ExpectedMessage> expectedMessages;
        private int inMessageCounter = 0;

        private final List<MessageReport> reports = new ArrayList<>();

        private final ExecutorService sendExecutor;
        private String sessionId;
        private WebSocket webSocket;

        private TestListener(List<ExpectedMessage> expectedMessages) {
            super();
            this.expectedMessages = expectedMessages;
            this.sendExecutor = Executors.newSingleThreadExecutor();
            /* we will put reports to conversationResult when we get all expected messages */
            this.conversationResult = new ArrayBlockingQueue<>(1);
        }

        @Override
        public void close() {
            closed = true;
            sendExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        webSocket.close(1000, "OK");
                    } catch (IOException e) {
                        log.warning("Could not close WebSocket");
                    }
                }
            });
            sendExecutor.shutdown();
            /* we are done, we can let the validation thread in */
            try {
                conversationResult.put(Collections.unmodifiableList(reports));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * @return a {@code sessionId} extracted from a welcome message.
         */
        public String getSessionId() {
            if (sessionId == null) {
                throw new IllegalStateException(
                        "sessionId was not initialized yet. A welcome message has probably not arrived yet.");
            }
            return sessionId;
        }

        public void onClose(int code, String reason) {
            log.fine("WebSocket closed");
        }

        public void onFailure(IOException e, Response response) {
            log.log(java.util.logging.Level.FINE, "WebSocket failure", e);
            Assert.fail("Unexpected " + getClass().getName() + ".onFailure()", e);
        }

        public void onMessage(BufferedSource payload, PayloadType type) throws IOException {
            if (closed) {
                throw new IllegalStateException("Received [" + (inMessageCounter + 1) + "]th message when only ["
                        + expectedMessages.size() + "] messages were expected");
            }
            ReusableBuffer message = new ReusableBuffer(payload);
            log.fine("Received message[" + inMessageCounter + "] [" + type + "] from WebSocket: "
                    + message.toString() + "]");
            ExpectedMessage expectedEvent = expectedMessages.get(inMessageCounter);
            MessageReport report = expectedEvent.assertMatch(this, inMessageCounter, message, type);
            log.fine(report.toString());
            inMessageCounter++;
            if (expectedEvent.hasAnswer()) {
                this.send(expectedEvent.getTextAnswer(), expectedEvent.getBinaryAnswer());
            }

            reports.add(report);
            if (inMessageCounter == expectedMessages.size()) {
                /* this was the last expected message */
                close();
            }
        }

        public void onOpen(WebSocket webSocket, Response response) {
            log.fine("WebSocket opened");
            this.webSocket = webSocket;
        }

        public void onPong(Buffer payload) {
        }

        public void send(final String text, final URL dataUrl) {
            sendExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try (Buffer b1 = new Buffer()) {
                        if (text != null) {
                            log.fine("Sending over WebSocket: " + text);
                            b1.writeUtf8(text);
                        }
                        if (dataUrl != null) {
                            try (InputStream in = dataUrl.openStream()) {
                                int b;
                                while ((b = in.read()) != -1) {
                                    b1.writeByte(b);
                                    // System.out.println("Writing binary data");
                                }
                            }
                        }
                        webSocket.sendMessage(dataUrl == null ? PayloadType.TEXT : PayloadType.BINARY, b1);
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to send message", e);
                    }
                }
            });
        }

        /**
         * @param timeout in milliseconds
         * @throws Throwable rethrown from the underlying {@link MessageReport}s
         */
        public void validate(long timeout) throws Throwable {

            List<MessageReport> finalReports = conversationResult.poll(timeout, TimeUnit.MILLISECONDS);

            List<Throwable> errors = new ArrayList<>();

            if (finalReports != null) {
                for (MessageReport report : finalReports) {
                    if (!report.passed()) {
                        errors.add(report.getThrowable());
                    }
                }
            } else {
                errors.add(new Throwable("Could not get conversation results after " + timeout + "ms"));
            }

            switch (errors.size()) {
                case 0:
                    /* passed */
                    return;
                case 1:
                    throw errors.get(0);
                default:
                    /* Several errors - report the number and the first one */
                    Throwable e = errors.get(0);
                    throw new AssertionError("[" + errors.size() + "] assertion errors, the first one being ["
                            + e.getMessage() + "]", e);
            }

        }

    }

    /**
     * A extension of {@link Matcher} that allows us to use Mustache placeholders in expected patterns.
     *
     * @param <T> the type of the object to match
     */
    protected interface WebSocketArgumentMatcher<T> extends org.hamcrest.Matcher<T> {
        default boolean matches(T actual, TestListener testListener) {
            return matches(actual);
        }
    }

    /**
     * Matches a WelcomeResponse, extracts a {@code sessionId} out of it and sets {@link TestListener#sessionId}.
     */
    public static class WelcomeMatcher extends PatternMatcher {

        private String sessionId;

        public WelcomeMatcher() {
            super("\\QWelcomeResponse={\"sessionId\":\"\\E.*");
        }

        public String getSessionId() {
            return sessionId;
        }

        @Override
        public boolean matches(ReusableBuffer actual, TestListener testListener) {
            if (super.matches(actual, testListener)) {
                BasicMessageWithExtraData<WelcomeResponse> envelope =
                        new ApiDeserializer().deserialize(actual.getTextPart());
                String sessionId = envelope.getBasicMessage().getSessionId();
                testListener.sessionId = sessionId;
                return true;
            }
            return false;
        }

    }

    /**
     * Asserts that the given {@link InputStream} is a {@link ZipInputStream} with at leat one {@link ZipEntry}.
     */
    public static class ZipWithOneEntryMatcher extends TypeSafeMatcher<InputStream> {

        /** @see org.hamcrest.SelfDescribing#describeTo(org.hamcrest.Description) */
        @Override
        public void describeTo(Description description) {
            description.appendText("expected ZIP stream");
        }

        /** @see org.hamcrest.TypeSafeMatcher#matchesSafely(java.lang.Object) */
        @Override
        protected boolean matchesSafely(InputStream in) {

            try (ZipInputStream zipInputStream = new ZipInputStream(in)) {
                ZipEntry entry = zipInputStream.getNextEntry();
                // it should be enough to assert that it's a valid ZIP file
                // when the ZIP is not valid, the entry will be null
                Assert.assertNotNull(entry);
                Assert.assertNotNull(entry.getName());
                // if we have a valid ZIP, we should be able to get at least one entry of it
                return true;
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }

    }

    private static final Logger log = Logger.getLogger(TestWebSocketClient.class.getName());

    /**
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    protected final OkHttpClient client;

    private final TestListener listener;

    private TestWebSocketClient(Request request, TestListener testListener) {
        super();
        if (request == null) {
            throw new IllegalStateException(
                    "Cannot build a [" + TestWebSocketClient.class.getName() + "] with a null request");
        }
        this.listener = testListener;
        this.client = new OkHttpClient();
        WebSocketCall.create(client, request).enqueue(testListener);
    }

    /** @see java.io.Closeable#close() */
    @Override
    public void close() throws IOException {
        client.getDispatcher().getExecutorService().shutdown();
    }

    /**
     * @param timeout in milliseconds
     * @throws Throwable rethrown from the underlying {@link MessageReport}s
     */
    public void validate(long timeout) throws Throwable {
        listener.validate(timeout);
    }

}
