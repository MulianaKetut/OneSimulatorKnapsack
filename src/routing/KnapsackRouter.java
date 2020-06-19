/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package routing;

import core.*;
import java.util.Collection;
import java.util.Map;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import routing.rapid.DelayEntry;
import routing.rapid.DelayTable;
import routing.rapid.MeetingEntry;

/**
 *
 * @author Gregorius Bima, Sanata Dharma Univeristy
 */
public class KnapsackRouter implements RoutingDecisionEngine {

    // delay table which contains meta data
    private DelayTable delayTable;

    private final UtilityAlgorithm ALGORITHM = UtilityAlgorithm.AVERAGE_DELAY;

    private static final double INFINITY = 99999;

    private DTNHost host;

    public KnapsackRouter(Settings s) {

    }

    public KnapsackRouter(KnapsackRouter prototype) {

    }

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {

    }

    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        
    }

    @Override
    public boolean newMessage(Message m) {
//        m.addProperty("utility", 0);
        return true;
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        return m.getTo() == aHost;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        return !thisHost.getRouter().hasMessage(m.getId());
    }

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost) {
     
        
        
        return true;
    }

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        return false;
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        return false;
    }

    private KnapsackRouter getOtherKnapsackRouter(DTNHost host) {
        MessageRouter otherRouter = host.getRouter();
        assert otherRouter instanceof DecisionEngineRouter : "This router only works "
                + " with other routers of same type";

        return (KnapsackRouter) ((DecisionEngineRouter) otherRouter).getDecisionEngine();
    }

    @Override
    public RoutingDecisionEngine replicate() {
        return new KnapsackRouter(this);
    }

    private enum UtilityAlgorithm {
        AVERAGE_DELAY,
//        MISSED_DEADLINES,
//        MAXIMUM_DELAY;
    }

    public DelayTable getDelayTable() {
        return delayTable;
    }

    /**
     * Returns the host this router is in
     *
     * @return The host object
     */
    protected DTNHost getHost() {
        return this.host;
    }
    
    /**
     * Returns the current marginal utility (MU) value for a connection of two
     * nodes
     *
     * @param msg One message of the message queue
     * @param con The connection
     * @param host The host which contains a copy of this message
     * @return the current MU value
     */
//    private double getMarginalUtility(Message msg, DTNHost host) {
////        final RapidRouter otherRouter = (RapidRouter) (con.getOtherNode(host).getRouter());
//        
//        final KnapsackRouter otherRouter = getOtherKnapsackRouter(host);
//
//        return getMarginalUtility(msg, otherRouter, host);
//    }
//    
    /**
     * Returns the current marginal utility (MU) value for a host of a specified
     * router
     *
     * @param msg One message of the message queue
     * @param router The router which the message could be send to
     * @param host The host which contains a copy of this message
     * @return the current MU value
     */
    private double getMarginalUtility(Message msg,  DTNHost host) {
        double marginalUtility = 0.0;
        double utility = 0.0;		// U(i): The utility of the message (packet) i
        double utilityOld = 0.0;
        KnapsackRouter de = getOtherKnapsackRouter(host);
        assert (this != de);
        utility = de.computeUtility(msg, host, true);
        utilityOld = this.computeUtility(msg, host, true);

        // s(i): The size of message i
        // delta(U(i)) / s(i)
        if ((utilityOld == -INFINITY) && (utility != -INFINITY)) {
            marginalUtility = (Math.abs(utility) / msg.getSize());
        } else if ((utility == -INFINITY) && (utilityOld != -INFINITY)) {
            marginalUtility = (Math.abs(utilityOld) / msg.getSize());
        } else if ((utility == utilityOld) && (utility != -INFINITY)) {
            marginalUtility = (Math.abs(utility) / msg.getSize());
        } else {
            marginalUtility = (utility - utilityOld) / msg.getSize();
        }

        return marginalUtility;
    }

    private double computeUtility(Message msg, DTNHost host, boolean recompute) {
        double utility = 0.0;		// U(i): The utility of the message (packet) i
        double packetDelay = 0.0;	// D(i): The expected delay of message i

        switch (ALGORITHM) {
            // minimizing average delay
            // U(i) = -D(i)
            case AVERAGE_DELAY: {
                packetDelay = estimateDelay(msg, host, recompute);
                utility = -packetDelay;
                break;
            }
            //dll di rapid
        }

        return utility;
    }

    /**
     * Returns the expected delay for a message if this message was sent to a
     * specified host
     *
     * @param msg The message which will be transfered
     * @return the expected packet delay
     */
    private double estimateDelay(Message msg, DTNHost host, boolean recompute) {
        double remainingTime = 0.0;	//a(i): random variable that determines the remaining time to deliver message i
        double packetDelay = 0.0;	//D(i): expected delay of message i

//        //if delay table entry for this message doesn't exist or the delay table
//        //has changed recompute the delay entry
        if ((recompute) && ((delayTable.delayHasChanged(msg.getId())) || (delayTable.getDelayEntryByMessageId(msg.getId()).getDelayOf(host) == null) /*|| (delayTable.getEntryByMessageId(msg.getId()).getDelayOf(host) == INFINITY))*/)) {
            // compute remaining time by using metadata
            remainingTime = computeRemainingTime(msg);
            packetDelay = Math.min(INFINITY, computePacketDelay(msg, host, remainingTime));

            //update delay table
            updateDelayTableEntry(msg, host, packetDelay, SimClock.getTime());
        } else {
            packetDelay = delayTable.getDelayEntryByMessageId(msg.getId()).getDelayOf(host);
        }

        return packetDelay;
    }

    private double computeRemainingTime(Message msg) {
        double transferTime = INFINITY;		//MX(i):random variable for corresponding transfer time delay
        double remainingTime = 0.0;		//a(i): random variable that determines the remaining time to deliver message i

        remainingTime = computeTransferTime(msg, msg.getTo());
        if (delayTable.getDelayEntryByMessageId(msg.getId()) != null) {
            //Entry<DTNHost host, Tuple<Double delay, Double lastUpdate>>
            for (Map.Entry<DTNHost, Tuple<Double, Double>> entry : delayTable.getDelayEntryByMessageId(msg.getId()).getDelays()) {
                DTNHost host = entry.getKey();
                if (host == getHost()) {
                    continue;	// skip
                }
                //transferTime = ((RapidRouter) host.getRouter()).computeTransferTime(msg, msg.getTo()); //MXm(i)	with m element of [0...k]
                transferTime = getOtherKnapsackRouter(host).computeTransferTime(msg, msg.getTo());
                remainingTime = Math.min(transferTime, remainingTime);	//a(i) = min(MX0(i), MX1(i), ... ,MXk(i)) 
            }
        }

        return remainingTime;
    }

    private double computeTransferTime(Message msg, DTNHost host) {
        //coba getHost
        Collection<Message> msgCollection = getHost().getMessageCollection();
//		List<Tuple<Message, Double>> list=new ArrayList<Tuple<Message,Double>>();
        double transferOpportunity = 0;					//B:    expected transfer opportunity in bytes between X and Z
        double packetsSize = 0.0;						//b(i): sum of sizes of packets that precede the actual message
        double transferTime = INFINITY;					//MX(i):random variable for corresponding transfer time delay
        double meetingTime = 0.0;						//MXZ:  random variable that represent the meeting Time between X and Y

        // packets are already sorted in decreasing order of receive time
        // sorting packets in decreasing order of time since creation is optional
//		// sort packets in decreasing order of time since creation
//		double timeSinceCreation = 0;
//		for (Message m : msgCollection) {
//			timeSinceCreation = SimClock.getTime() - m.getCreationTime();
//			list.add(new Tuple<Message, Double>(m, timeSinceCreation));
//		}
//		Collections.sort(list, new TupleComparator2());
        // compute sum of sizes of packets that precede the actual message 
        for (Message m : msgCollection) {
//		for (Tuple<Message, Double> t : list) {
//			Message m = t.getKey();
            if (m.equals(msg)) {
                continue; //skip
            }
            packetsSize = packetsSize + m.getSize();
        }

        // compute transfer time  
        transferOpportunity = delayTable.getAvgTransferOpportunity();	//B in bytes

        MeetingEntry entry = delayTable.getIndirectMeetingEntry(this.getHost().getAddress(), host.getAddress());
        // a meeting entry of null means that these hosts have never met -> set transfer time to maximum
        if (entry == null) {
            transferTime = INFINITY;		//MX(i)
        } else {
            meetingTime = entry.getAvgMeetingTime();	//MXZ
            transferTime = meetingTime * Math.ceil(packetsSize / transferOpportunity);	// MX(i) = MXZ * ceil[b(i) / B]
        }

        return transferTime;
    }

    private double computePacketDelay(Message msg, DTNHost host, double remainingTime) {
        double timeSinceCreation = 0.0;		//T(i): time since creation of message i 
        double expectedRemainingTime = 0.0;	//A(i): expected remaining time E[a(i)]=A(i)
        double packetDelay = 0.0;		//D(i): expected delay of message i

        //TODO E[a(i)]=Erwartungswert von a(i) mit a(i)=remainingTime
        expectedRemainingTime = remainingTime;

        // compute packet delay
        timeSinceCreation = SimClock.getTime() - msg.getCreationTime();
        packetDelay = timeSinceCreation + expectedRemainingTime;

        return packetDelay;
    }

    /**
     * Updates an delay table entry for given message and host
     *
     * @param m The message
     * @param host The host which contains a copy of this message
     * @param delay The estimated delay
     * @param time The entry was last changed
     */
    public void updateDelayTableEntry(Message m, DTNHost host, double delay, double time) {
        DelayEntry delayEntry;

        if ((delayEntry = delayTable.getDelayEntryByMessageId(m.getId())) == null) {
            delayEntry = new DelayEntry(m);
            delayTable.addEntry(delayEntry);
        }
        assert ((delayEntry != null) && (delayTable.getDelayEntryByMessageId(m.getId()) != null));

        if (delayEntry.contains(host)) {
            delayEntry.setHostDelay(host, delay, time);
        } else {
            delayEntry.addHostDelay(host, delay, time);
        }
    }
}
