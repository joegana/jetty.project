//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.common.message;

import static org.hamcrest.Matchers.is;

import java.net.URI;
import java.util.Arrays;

import org.eclipse.jetty.toolchain.test.TestTracker;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.extensions.OutgoingFrames;
import org.eclipse.jetty.websocket.common.WebSocketSession;
import org.eclipse.jetty.websocket.common.io.FramePipes;
import org.eclipse.jetty.websocket.common.io.LocalWebSocketConnection;
import org.eclipse.jetty.websocket.common.scopes.SimpleContainerScope;
import org.eclipse.jetty.websocket.common.scopes.WebSocketContainerScope;
import org.eclipse.jetty.websocket.common.test.LeakTrackingBufferPoolRule;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class MessageWriterTest
{
    private static final Logger LOG = Log.getLogger(MessageWriterTest.class);

    @Rule
    public TestTracker testtracker = new TestTracker();

    @Rule
    public TestName testname = new TestName();

    @Rule
    public LeakTrackingBufferPoolRule bufferPool = new LeakTrackingBufferPoolRule("Test");

    private WebSocketPolicy policy;
    private TrackingSocket remoteSocket;
    private WebSocketSession session;
    private WebSocketSession remoteSession;

    @After
    public void closeSession() throws Exception
    {
        session.close();
        remoteSession.close();
        remoteSession.stop();
    }

    @Before
    public void setupSession() throws Exception
    {
        policy = WebSocketPolicy.newServerPolicy();
        policy.setInputBufferSize(1024);
        policy.setMaxTextMessageBufferSize(1024);

        // Container
        WebSocketContainerScope containerScope = new SimpleContainerScope(policy,bufferPool);

        // remote socket
        remoteSocket = new TrackingSocket("remote");
        URI remoteURI = new URI("ws://localhost/remote");
        LocalWebSocketConnection remoteConnection = new LocalWebSocketConnection(bufferPool);
        remoteSession = new WebSocketSession(containerScope,remoteURI,remoteSocket,remoteConnection);
        OutgoingFrames socketPipe = FramePipes.to(remoteSession);
        remoteSession.start();
        remoteSession.open();

        // Local Session
        TrackingSocket localSocket = new TrackingSocket("local");
        URI localURI = new URI("ws://localhost/local");
        LocalWebSocketConnection localConnection = new LocalWebSocketConnection(bufferPool);
        session = new WebSocketSession(containerScope,localURI,localSocket,localConnection);

        session.setPolicy(policy);
        // talk to our remote socket
        session.setOutgoingHandler(socketPipe);
        // start session
        session.start();
        // open connection
        session.open();
    }

    @Test
    public void testMultipleWrites() throws Exception
    {
        try (MessageWriter stream = new MessageWriter(session))
        {
            stream.write("Hello");
            stream.write(" ");
            stream.write("World");
        }

        Assert.assertThat("Socket.messageQueue.size",remoteSocket.messageQueue.size(),is(1));
        String msg = remoteSocket.messageQueue.poll();
        Assert.assertThat("Message",msg,is("Hello World"));
    }

    @Test
    public void testSingleWrite() throws Exception
    {
        try (MessageWriter stream = new MessageWriter(session))
        {
            stream.append("Hello World");
        }

        Assert.assertThat("Socket.messageQueue.size",remoteSocket.messageQueue.size(),is(1));
        String msg = remoteSocket.messageQueue.poll();
        Assert.assertThat("Message",msg,is("Hello World"));
    }

    @Test
    public void testWriteMultipleBuffers() throws Exception
    {
        int bufsize = (int)(policy.getMaxTextMessageBufferSize() * 2.5);
        char buf[] = new char[bufsize];
        if (LOG.isDebugEnabled())
            LOG.debug("Buffer size: {}",bufsize);
        Arrays.fill(buf,'x');
        buf[bufsize - 1] = 'o'; // mark last entry for debugging

        try (MessageWriter stream = new MessageWriter(session))
        {
            stream.write(buf);
        }

        Assert.assertThat("Socket.messageQueue.size",remoteSocket.messageQueue.size(),is(1));
        String msg = remoteSocket.messageQueue.poll();
        String expected = new String(buf);
        Assert.assertThat("Message",msg,is(expected));
    }
}
