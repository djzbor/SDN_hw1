/**
*    Copyright 2013, Columbia University.
*    Homework 1, COMS E6998-8 Fall 2013
*    Software Defined Networking
*    Originally created by YoungHoon Jung, Columbia University
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

/**
 * Floodlight
 * A BSD licensed, Java based OpenFlow controller
 *
 * Floodlight is a Java based OpenFlow controller originally written by David Erickson at Stanford
 * University. It is available under the BSD license.
 *
 * For documentation, forums, issue tracking and more visit:
 *
 * http://www.openflowhub.org/display/Floodlight/Floodlight+Home
 **/

package edu.columbia.cs6998.sdn.hw1;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightTestModuleLoader;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFFlowRemoved.OFFlowRemovedReason;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class Hw1SwitchTest4 extends FloodlightTestCase {
    protected OFPacketIn packetIn;
    protected OFPacketIn packetInReply;
    protected IPacket testPacket;
    protected byte[] testPacketSerialized;
    protected IPacket testPacketReply;
    protected byte[] testPacketReplySerialized;
    private Hw1Switch hw1Switch;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        FloodlightTestModuleLoader fml = new FloodlightTestModuleLoader();
        Collection<Class<? extends IFloodlightModule>> mods 
            = new ArrayList<Class<? extends IFloodlightModule>>();
        mods.add(Hw1Switch.class);
        fml.setupModules(mods, null);
        hw1Switch = (Hw1Switch) fml.getModuleByName(Hw1Switch.class);
        mockFloodlightProvider = 
            (MockFloodlightProvider) fml.getModuleByName(MockFloodlightProvider.class);
       
        // Build our test packet
        this.testPacket = new Ethernet()
            .setDestinationMACAddress("00:11:22:33:44:55")
            .setSourceMACAddress("00:44:33:22:11:00")
            .setVlanID((short) 42)
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                            .setSourcePort((short) 5000)
                            .setDestinationPort((short) 5001)
                            .setPayload(new Data(new byte[] {0x01}))));
        this.testPacketSerialized = testPacket.serialize();
        this.testPacketReply = new Ethernet()
            .setDestinationMACAddress("00:44:33:22:11:00")
            .setSourceMACAddress("00:11:22:33:44:55")
            .setVlanID((short) 42)
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                new IPv4()
                    .setTtl((byte) 128)
                    .setSourceAddress("192.168.1.2")
                    .setDestinationAddress("192.168.1.1")
                    .setPayload(new UDP()
                        .setSourcePort((short) 5001)
                        .setDestinationPort((short) 5000)
                        .setPayload(new Data(new byte[] {0x02}))));
        this.testPacketReplySerialized = testPacketReply.serialize();

        // Build the PacketIn
        this.packetIn = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.testPacketSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.testPacketSerialized.length);

        // Build the PacketIn
        this.packetInReply = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 2)
            .setPacketData(this.testPacketReplySerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.testPacketSerialized.length);
    }

    @Test
    public void testTwoSwitchesFlood() throws Exception {
        // build our expected flooded packetOut
        OFPacketOut po1 = new OFPacketOut()
            .setActions(Arrays.asList(new OFAction[] {new OFActionOutput().setPort(OFPort.OFPP_FLOOD.getValue())}))
            .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.testPacketSerialized);
        po1.setLengthU(OFPacketOut.MINIMUM_LENGTH + po1.getActionsLengthU()
                + this.testPacketSerialized.length);

        OFPacketOut po2 = new OFPacketOut()
            .setActions(Arrays.asList(new OFAction[] {new OFActionOutput().setPort(OFPort.OFPP_FLOOD.getValue())}))
            .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
            .setBufferId(-1)
            .setInPort((short) 2)
            .setPacketData(this.testPacketSerialized);
        po2.setLengthU(OFPacketOut.MINIMUM_LENGTH + po2.getActionsLengthU()
                + this.testPacketSerialized.length);

        // Mock up our expected behavior
        IOFSwitch mockSwitch1 = createMock(IOFSwitch.class);
        IOFSwitch mockSwitch2 = createMock(IOFSwitch.class);
        expect(mockSwitch1.getStringId()).andReturn("00:11:22:33:44:55:66:77").anyTimes();
        expect(mockSwitch2.getStringId()).andReturn("00:11:22:33:44:55:66:77").anyTimes();
        mockSwitch1.write(po1, null);
        mockSwitch2.write(po2, null);

        // Start recording the replay on the mocks
        replay(mockSwitch1);
        replay(mockSwitch2);
        // Get the listener and trigger the packet in
        IOFMessageListener listener = mockFloodlightProvider.getListeners().get(
                OFType.PACKET_IN).get(0);

        this.packetIn.setBufferId(-1).setInPort((short) 1);
        listener.receive(mockSwitch1, this.packetIn, parseAndAnnotate(this.packetIn));
        this.packetIn.setBufferId(-1).setInPort((short) 2);
        listener.receive(mockSwitch2, this.packetIn, parseAndAnnotate(this.packetIn));

        verify(mockSwitch1);
        verify(mockSwitch2);
    }
}
