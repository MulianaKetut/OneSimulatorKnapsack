/* 
 * Copyright 2010 Institute of Telematics, Karlsruhe Institute of Technology (KIT)
 * Released under GPLv3. See LICENSE.txt for details. 
 * 
 * Christoph P. Mayer - mayer[at]kit[dot]edu
 * Wolfgang Heetfeld
 *
 * Version 0.1 - released 13. Oct 2010
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import routing.rapid.DelayEntry;
import routing.rapid.DelayTable;
import routing.rapid.MeetingEntry;

import core.*;
import java.util.Iterator;
import java.util.LinkedList;
import routing.community.Duration;

/**
 * RAPID router
 */
public class KnapsackRouter extends ActiveRouter {
    // timestamp for meeting a host in seconds

    private double timestamp;
//    private Map<DTNHost, List<Message>> report;
    // delay table which contains meta data
    private DelayTable delayTable;
    // utility algorithm (minimizing average delay | minimizing missed deadlines |
    // minimizing maximum delay)
    private Map<Integer, DTNHost> hostMapping;
    
    private final UtilityAlgorithm ALGORITHM = UtilityAlgorithm.AVERAGE_DELAY;
    private static final double INFINITY = 99999;

    // interval to verify ongoing connections in seconds
    private static final double UTILITY_INTERAL = 100.0;
    
    LinkedList<Double> utilityMsg;
    LinkedList<Integer> lengthMsg;
    
    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

//    private Knapsack knapsack;
    /**
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object.
     *
     * @param s The settings object
     */
    public KnapsackRouter(Settings s) {
        super(s);
        
        delayTable = null;
        timestamp = 0.0;
        hostMapping = new HashMap<Integer, DTNHost>();
        startTimestamps = new HashMap<DTNHost, Double>();
        utilityMsg = new LinkedList<Double>();
        lengthMsg = new LinkedList<Integer>();
    }
    
    @Override
    public void initialize(DTNHost host, List<MessageListener> mListeners) {
        super.initialize(host, mListeners);
        
        delayTable = new DelayTable(super.getHost());
        startTimestamps = new HashMap<DTNHost, Double>();
        connHistory = new HashMap<DTNHost, List<Duration>>();
        utilityMsg = new LinkedList<Double>();
        lengthMsg = new LinkedList<Integer>();
    }

    /**
     * Copy constructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected KnapsackRouter(KnapsackRouter r) {
        super(r);
        
        delayTable = r.delayTable;
        timestamp = r.timestamp;
        hostMapping = r.hostMapping;
        utilityMsg = r.utilityMsg;
        lengthMsg = r.lengthMsg;
        startTimestamps = r.startTimestamps;
    }
    
    @Override
    public void changedConnection(Connection con) {
        DTNHost peer = con.getOtherNode(getHost());
        DTNHost myHost = getHost();
        KnapsackRouter de = (KnapsackRouter) peer.getRouter();
        
        this.startTimestamps.put(peer, SimClock.getTime());
        de.startTimestamps.put(myHost, SimClock.getTime());
        
        if (con.isUp()) {
            /* new connection */
            //simulate control channel on connection up without sending any data
            timestamp = SimClock.getTime();
            //synchronize all delay table entries
            synchronizeDelayTables(con);
            //synchronize all meeting time entries 
            synchronizeMeetingTimes(con);
            updateDelayTableStat(con);
            //synchronize acked message ids
            synchronizeAckedMessageIDs(con);
            deleteAckedMessages();
            //map DTNHost to their address
            doHostMapping(con);
            delayTable.dummyUpdateConnection(con);
            
            System.out.println("speed tf " + getTransferSpeed());
            System.out.println("transmit range " + getTransmitRange());
            System.out.println("transmit time " + getTransmitTime());
            System.out.println("avg Duration " + getAverageDurationOfNodes(peer));
            System.out.println("move speed " + getMovementSpeed());

//            updateUtilityMsg(con);
        } else {
            /* connection went down */
            double currentTime = startTimestamps.get(peer);
            double lastConnect = SimClock.getTime();

            // Find or create the connection history list
            List<Duration> history;
            if (!connHistory.containsKey(peer)) {
                history = new LinkedList<Duration>();
                connHistory.put(peer, history);
            } else {
                history = connHistory.get(peer);
            }

            // add this connection to the list
            if (lastConnect - currentTime > 0) {
                history.add(new Duration(currentTime, lastConnect));
            }

            //update connection
            double time = SimClock.getTime() - timestamp;
            delayTable.updateConnection(con, time);
            //synchronize acked message ids
            synchronizeAckedMessageIDs(con);
            //update set of messages that are known to have reached the destination 
            deleteAckedMessages();
            updateAckedMessageIds();
            //synchronize all delay table entries
            synchronizeDelayTables(con);
            
            startTimestamps.remove(peer);
        }
    }
    
    public void ckeckConnectionStatus() {
        
        DTNHost host = getHost();
        int from = host.getAddress();
        double checkPeriod = 0.0;
        
        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(host);
            int to = other.getAddress();
            KnapsackRouter otherRouter = (KnapsackRouter) other.getRouter();
            MeetingEntry entry = otherRouter.getDelayTable().getMeetingEntry(from, to);
            checkPeriod = SimClock.getTime() - UTILITY_INTERAL;
            
            if (con.isUp() && entry.isOlderThan(checkPeriod)) {
                // simulate artificial break 
                //update connection
                double time = (SimClock.getTime() - 0.1) - timestamp;
                delayTable.updateConnection(con, time);
                //synchronize acked message ids
                synchronizeAckedMessageIDs(con);
                //update set of messages that are known to have reached the destination 
                deleteAckedMessages();
                updateAckedMessageIds();
                //synchronize all delay table entries
                synchronizeDelayTables(con);
                // simulate artificial make
                //simulate control channel on connection up without sending any data
                timestamp = SimClock.getTime();
                //synchronize all delay table entries
                synchronizeDelayTables(con);
                //synchronize all meeting time entries 
                synchronizeMeetingTimes(con);
                updateDelayTableStat(con);
                //synchronize acked message ids
                synchronizeAckedMessageIDs(con);
                deleteAckedMessages();
                //map DTNHost to their address
                doHostMapping(con);
                delayTable.dummyUpdateConnection(con);
            }
        }
    }
    
    private void doHostMapping(Connection con) {
        DTNHost host = getHost();
        DTNHost otherHost = con.getOtherNode(host);
        KnapsackRouter otherRouter = ((KnapsackRouter) otherHost.getRouter());

        // propagate host <-> address mapping
        hostMapping.put(host.getAddress(), host);
        hostMapping.put(otherHost.getAddress(), otherHost);
        hostMapping.putAll(otherRouter.hostMapping);
        otherRouter.hostMapping.putAll(hostMapping);
    }
    
    private void updateDelayTableStat(Connection con) {
        DTNHost otherHost = con.getOtherNode(getHost());
        KnapsackRouter otherRouter = ((KnapsackRouter) otherHost.getRouter());
        int from = otherHost.getAddress();
        
        for (Message m : getMessageCollection()) {
            int to = m.getTo().getAddress();
            MeetingEntry entry = otherRouter.getDelayTable().getMeetingEntry(from, to);
            if (entry != null) {
                delayTable.getDelayEntryByMessageId(m.getId()).setChanged(true);
            }
        }
    }
    
    private void synchronizeDelayTables(Connection con) {
        DTNHost otherHost = con.getOtherNode(getHost());
        KnapsackRouter otherRouter = (KnapsackRouter) otherHost.getRouter();
        DelayEntry delayEntry = null;
        DelayEntry otherDelayEntry = null;

        //synchronize all delay table entries
        for (Entry<String, DelayEntry> entry1 : delayTable.getDelayEntries()) {
            Message m = entry1.getValue().getMessage();
            delayEntry = delayTable.getDelayEntryByMessageId(m.getId());
            assert (delayEntry != null);
            otherDelayEntry = otherRouter.delayTable.getDelayEntryByMessageId(m.getId());
            
            if (delayEntry.getDelays() == null) {
                continue;
            }
            //for all hosts check delay entries and create new if they doesn't exist
            //Entry<DTNHost host, Tuple<Double delay, Double lastUpdate>>
            for (Entry<DTNHost, Tuple<Double, Double>> entry : delayEntry.getDelays()) {
                DTNHost myHost = entry.getKey();
                Double myDelay = entry.getValue().getKey();
                Double myTime = entry.getValue().getValue();

                //create a new host entry if host entry at other host doesn't exist
                if ((otherDelayEntry == null) || (!otherDelayEntry.contains(myHost))) {
                    //parameters: 
                    //m The message 
                    //myHost The host which contains a copy of this message
                    //myDelay The estimated delay
                    //myTime The entry was last changed
                    otherRouter.updateDelayTableEntry(m, myHost, myDelay, myTime);
                } else {
                    //check last update time of other hosts entry and update it 
                    if (otherDelayEntry.isOlderThan(myHost, delayEntry.getLastUpdate(myHost))) {
                        //parameters: 
                        //m The message 
                        //myHost The host which contains a copy of this message
                        //myDelay The estimated delay
                        //myTime The entry was last changed 

                        otherRouter.updateDelayTableEntry(m, myHost, myDelay, myTime);
                    }
                    
                    if ((otherDelayEntry.isAsOldAs(myHost, delayEntry.getLastUpdate(myHost))) && (delayEntry.getDelayOf(myHost) > otherDelayEntry.getDelayOf(myHost))) {
                        //parameters: 
                        //m The message 
                        //myHost The host which contains a copy of this message
                        //myDelay The estimated delay
                        //myTime The entry was last changed 

                        otherRouter.updateDelayTableEntry(m, myHost, myDelay, myTime);
                    }
                }
            }
        }
    }
    
    private void synchronizeMeetingTimes(Connection con) {
        DTNHost otherHost = con.getOtherNode(getHost());
        KnapsackRouter otherRouter = (KnapsackRouter) otherHost.getRouter();
        MeetingEntry meetingEntry = null;
        MeetingEntry otherMeetingEntry = null;

        //synchronize all meeting time entries
        for (int i = 0; i < delayTable.getMeetingMatrixDimension(); i++) {
            for (int k = 0; k < delayTable.getMeetingMatrixDimension(); k++) {
                meetingEntry = delayTable.getMeetingEntry(i, k);
                
                if (meetingEntry != null) {
                    otherMeetingEntry = otherRouter.delayTable.getMeetingEntry(i, k);
                    //create a new meeting entry if meeting entry at other host doesn't exist
                    if (otherMeetingEntry == null) {
                        otherRouter.delayTable.setAvgMeetingTime(i, k, meetingEntry.getAvgMeetingTime(), meetingEntry.getLastUpdate(), meetingEntry.getWeight());
                    } else {
                        //check last update time of other hosts entry and update it 
                        if (otherMeetingEntry.isOlderThan(meetingEntry.getLastUpdate())) {
                            otherRouter.delayTable.setAvgMeetingTime(i, k, meetingEntry.getAvgMeetingTime(), meetingEntry.getLastUpdate(), meetingEntry.getWeight());
                        }
                        
                        if ((otherMeetingEntry.isAsOldAs(meetingEntry.getLastUpdate())) && (meetingEntry.getAvgMeetingTime() > otherMeetingEntry.getAvgMeetingTime())) {
                            otherRouter.delayTable.setAvgMeetingTime(i, k, meetingEntry.getAvgMeetingTime(), meetingEntry.getLastUpdate(), meetingEntry.getWeight());
                        }
                    }
                }
            }
        }
    }
    
    private void synchronizeAckedMessageIDs(Connection con) {
        DTNHost otherHost = con.getOtherNode(getHost());
        KnapsackRouter otherRouter = (KnapsackRouter) otherHost.getRouter();
        
        delayTable.addAllAckedMessageIds(otherRouter.delayTable.getAllAckedMessageIds());
        otherRouter.delayTable.addAllAckedMessageIds(delayTable.getAllAckedMessageIds());
        
        assert (delayTable.getAllAckedMessageIds().equals(otherRouter.delayTable.getAllAckedMessageIds()));
    }

    /**
     * Deletes the messages from the message buffer that are known to be ACKed
     * and also delete the message entries of the ACKed messages in the delay
     * table
     */
    private void deleteAckedMessages() {
        for (String id : delayTable.getAllAckedMessageIds()) {
            if (this.hasMessage(id) && !isSending(id)) {
                //delete messages from the message buffer
                this.deleteMessage(id, false);
            }

            //delete messages from the delay table
            if (delayTable.getDelayEntryByMessageId(id) != null) {
                assert (delayTable.removeEntry(id) == true);
            }
        }
    }
    
    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message m = super.messageTransferred(id, from);
        /* was this node the final recipient of the message? */
        if (isDeliveredMessage(m)) {
            delayTable.addAckedMessageIds(id);
        }
        return m;
    }

    /**
     * Delete old message ids from the acked message id set which time to live
     * was passed
     */
    private void updateAckedMessageIds() {
        ArrayList<String> removableIds = new ArrayList<String>();
        
        for (String id : delayTable.getAllAckedMessageIds()) {
            Message m = this.getMessage(id);
            if ((m != null) && (m.getTtl() <= 0)) {
                removableIds.add(id);
            }
        }
        
        if (removableIds.size() > 0) {
            delayTable.removeAllAckedMessageIds(removableIds);
        }
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
    
    @Override
    public boolean createNewMessage(Message m) {
        boolean stat = super.createNewMessage(m);
        //if message was created successfully add the according delay table entry
        if (stat) {
            updateDelayTableEntry(m, getHost(), estimateDelay(m, getHost(), true), SimClock.getTime());
        }
        return stat;
    }
    
    @Override
    public int receiveMessage(Message m, DTNHost from) {
        if (getHost().getRouter().getFreeBufferSize() < m.getSize()) {
            List<Message> msg = new ArrayList<>(getHost().getMessageCollection());
            msg.add(m);
        }
        
        int stat = super.receiveMessage(m, from);
        //if message was received successfully add the according delay table entry
        if (stat == 0) {
            DTNHost host = getHost();
            double time = SimClock.getTime();
            double delay = estimateDelay(m, host, true);
            updateDelayTableEntry(m, host, delay, time);
            ((KnapsackRouter) from.getRouter()).updateDelayTableEntry(m, host, delay, time);
            //System.out.println("pesan masuk "+m.getId()+" value "+m.getProperty("value"));
        }
        
        return stat;
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
    private double getMarginalUtility(Message msg, Connection con, DTNHost host) {
        final KnapsackRouter otherRouter = (KnapsackRouter) (con.getOtherNode(host).getRouter());
        return getMarginalUtility(msg, otherRouter, host);
    }

    /**
     * Returns the current marginal utility (MU) value for a host of a specified
     * router
     *
     * @param msg One message of the message queue
     * @param router The router which the message could be send to
     * @param host The host which contains a copy of this message
     * @return the current MU value
     */
    private double getMarginalUtility(Message msg, KnapsackRouter router, DTNHost host) {
        double marginalUtility = 0.0;
        double utility = 0.0;			// U(i): The utility of the message (packet) i
        double utilityOld = 0.0;
        
        assert (this != router);
        utility = router.computeUtility(msg, host, true);
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
        double utility = 0.0;			// U(i): The utility of the message (packet) i
        double packetDelay = 0.0;		// D(i): The expected delay of message i

        switch (ALGORITHM) {
            // minimizing average delay
            // U(i) = -D(i)
            case AVERAGE_DELAY: {
                packetDelay = estimateDelay(msg, host, recompute);
                utility = -packetDelay;
                break;
            }
            // minimizing missed deadlines
            // L(i)   : The life time of message (packet) i
            // T(i)   : The time since creation of message i
            // a(i)   : A random variable that determines the remaining time to deliver message i
            // P(a(i)): The probability that the message will be delivered within its deadline
            //
            //  	   / P(a(i) < L(i) - T(i)) , L(i) > T(i)
            // U(i) = <|
            // 		   \           0   		   , otherwise
            case MISSED_DEADLINES: {
                //life time in seconds
                double lifeTime = msg.getTtl() * 60;
                //creation time in seconds
                double timeSinceCreation = SimClock.getTime() - msg.getCreationTime();
                double remainingTime = computeRemainingTime(msg);
                if (lifeTime > timeSinceCreation) {
                    // compute remaining time by using metadata
                    if (remainingTime < lifeTime - timeSinceCreation) {
                        utility = lifeTime - timeSinceCreation - remainingTime;
                    } else {
                        utility = 0;
                    }
                } else {
                    utility = 0;
                }
                packetDelay = computePacketDelay(msg, host, remainingTime);
                break;
            }
            // minimizing maximum delay
            // S   : Set of all message in buffer of X
            //
            // 		   / -D(i) , D(i) >= D(j) for all j element of S
            // U(i) = <|
            // 		   \   0   , otherwise
            case MAXIMUM_DELAY: {
                packetDelay = estimateDelay(msg, host, recompute);
                Collection<Message> msgCollection = getMessageCollection();
                for (Message m : msgCollection) {
                    if (m.equals(msg)) {
                        continue;	//skip 
                    }
                    if (packetDelay < estimateDelay(m, host, recompute)) {
                        packetDelay = 0;
                        break;
                    }
                }
                utility = -packetDelay;
                break;
            }
            default:
        }
        
        return utility;
    }

    /**
     * Estimate-Delay Algorithm
     *
     * 1) Sort packets in Q in decreasing order of T(i). Let b(i) be the sum of
     * sizes of packets that precede i, and B the expected transfer opportunity
     * in bytes between X and Z.
     *
     * 2. X by itself requires b(i)/B meetings with Z to deliver i. Compute the
     * random variable MX(i) for the corresponding delay as MX(i) = MXZ + MXZ +
     * . . . b(i)/B times (4)
     *
     * 3. Let X1 , . . . , Xk ⊇ X be the set of nodes possessing a replica of i.
     * Estimate remaining time a(i) as a(i) = min(MX1(i), . . . , MXk(i))
     *
     * 4. Expected delay D(i) = T(i) + E[a(i)]
     *
     */
    /**
     * Returns the expected delay for a message if this message was sent to a
     * specified host
     *
     * @param msg The message which will be transfered
     * @return the expected packet delay
     */
    private double estimateDelay(Message msg, DTNHost host, boolean recompute) {
        double remainingTime = 0.0;						//a(i): random variable that determines the	remaining time to deliver message i
        double packetDelay = 0.0;						//D(i): expected delay of message i

        //if delay table entry for this message doesn't exist or the delay table
        //has changed recompute the delay entry
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
        double transferTime = INFINITY;					//MX(i):random variable for corresponding transfer time delay
        double remainingTime = 0.0;						//a(i): random variable that determines the	remaining time to deliver message i

        remainingTime = computeTransferTime(msg, msg.getTo());
        if (delayTable.getDelayEntryByMessageId(msg.getId()) != null) {
            //Entry<DTNHost host, Tuple<Double delay, Double lastUpdate>>
            for (Entry<DTNHost, Tuple<Double, Double>> entry : delayTable.getDelayEntryByMessageId(msg.getId()).getDelays()) {
                DTNHost host = entry.getKey();
                if (host == getHost()) {
                    continue;	// skip
                }
                transferTime = ((KnapsackRouter) host.getRouter()).computeTransferTime(msg, msg.getTo()); //MXm(i)	with m element of [0...k]
                remainingTime = Math.min(transferTime, remainingTime);	//a(i) = min(MX0(i), MX1(i), ... ,MXk(i)) 
            }
        }
        return remainingTime;
    }
    
    private double computeTransferTime(Message msg, DTNHost host) {
        Collection<Message> msgCollection = getMessageCollection();
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
        double timeSinceCreation = 0.0;					//T(i): time since creation of message i 
        double expectedRemainingTime = 0.0;				//A(i): expected remaining time E[a(i)]=A(i)
        double packetDelay = 0.0;						//D(i): expected delay of message i

        //TODO E[a(i)]=Erwartungswert von a(i) mit a(i)=remainingTime
        expectedRemainingTime = remainingTime;

        // compute packet delay
        timeSinceCreation = SimClock.getTime() - msg.getCreationTime();
        packetDelay = timeSinceCreation + expectedRemainingTime;
        
        return packetDelay;
    }
    
    @Override
    public void update() {
        super.update();
        
        ckeckConnectionStatus();
        
        if (isTransferring() || !canStartTransfer()) {
            return;
        }

        // Try messages that can be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        // Otherwise do RAPID-style message exchange
        if (tryOtherMessages() != null) {
            return;
        }
    }
    
    private Tuple<Message, Connection> tryOtherMessages() {
//        List<Tuple<Tuple<Message, Connection>, Double>> messages = new ArrayList<Tuple<Tuple<Message, Connection>, Double>>();
        List<Tuple<Message, Connection>> messages = new LinkedList<Tuple<Message, Connection>>();
        Collection<Message> msgCollection = getMessageCollection();
        
        for (Connection con : getConnections()) {
            
            DTNHost other = con.getOtherNode(getHost());
            KnapsackRouter otherRouter = (KnapsackRouter) other.getRouter();
            updateUtilityMsg(con);
            
            if (otherRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }
            
            LinkedList<Integer> knapsack = getKnapsackSend(other);
            for (Message m : msgCollection) {
                for (int i = 0; i <= knapsack.size(); i++) {
                    if (knapsack.get(i) == 1) {
                        messages.add(new Tuple<Message, Connection>(m, con));
                        break;
                    } else {
                        break;
                    }
                }
            }
        }
        delayTable.setChanged(false);
        if (messages == null) {
            return null;
        }
        
        return tryMessagesForConnected(messages);	// try to send messages
    }
    
    public DelayTable getDelayTable() {
        return delayTable;
    }
    
    @Override
    public KnapsackRouter replicate() {
        return new KnapsackRouter(this);
    }
    
    private enum UtilityAlgorithm {
        AVERAGE_DELAY,
        MISSED_DEADLINES,
        MAXIMUM_DELAY;
    }
    
    public void updateUtilityMsg(Connection con) {
        Double util = 0.0;
//        LinkedList<Double> utility = new LinkedList<>();
//        LinkedList<Integer> length = new LinkedList<>();
        for (Message m : this.getHost().getMessageCollection()) {
            if (m == null) {
                break;
            } else {
                util = getMarginalUtility(m, con, getHost());
                System.out.println(util);
                utilityMsg.add(util);
                lengthMsg.add(m.getSize());
            }
        }
    }
    
    public LinkedList<Integer> getKnapsackDrop() {
        LinkedList<Integer> knapsackMsg = new LinkedList<>();
        int n = utilityMsg.size();
        int i, length;
        int bufferSize = getHost().getRouter().getBufferSize();
        double bestSolution[][] = new double[n + 1][bufferSize + 1];
        
        for (i = 0; i <= n; i++) {
            for (length = 0; length <= bufferSize; length++) {
                if (i == 0 || length == 0) {
                    bestSolution[i][length] = 0;
                } else if (lengthMsg.get(i - 1) <= length) {
                    bestSolution[i][length] = Math.max(bestSolution[i - 1][length],
                            utilityMsg.get(i - 1) + bestSolution[i - 1][length - lengthMsg.get(i - 1)]);
                } else {
                    bestSolution[i][length] = bestSolution[i - 1][length];
                }
            }
        }
        for (int j = n; j >= 1; i++) {
            if (bestSolution[j][bufferSize] > bestSolution[j - 1][bufferSize]) {
                knapsackMsg.addFirst(1);
            } else {
                knapsackMsg.addFirst(0);
            }
        }
        return knapsackMsg;
    }
    
    public LinkedList<Integer> getKnapsackSend(DTNHost peer) {
        LinkedList<Integer> knapsackMsg = new LinkedList<>();
        double avgMeeting = getAverageDurationOfNodes(peer);
        double transferSpeed = getTransferSpeed();
        int n = utilityMsg.size();
        int i, length;
        int bufferSize = getHost().getRouter().getBufferSize();
        double bestSolution[][] = new double[n + 1][bufferSize + 1];
        
        for (i = 0; i <= n; i++) {
            for (length = 0; length <= bufferSize; length++) {
                if (i == 0 || length == 0) {
                    bestSolution[i][length] = 0;
                } else if (lengthMsg.get(i - 1) <= length) {
                    bestSolution[i][length] = Math.max(bestSolution[i - 1][length],
                            utilityMsg.get(i - 1) + bestSolution[i - 1][length - lengthMsg.get(i - 1)]);
                } else {
                    bestSolution[i][length] = bestSolution[i - 1][length];
                }
            }
        }
        for (int j = n; j >= 1; i++) {
            if (bestSolution[j][bufferSize] > bestSolution[j - 1][bufferSize]) {
                knapsackMsg.addFirst(1);
                bufferSize = bufferSize - lengthMsg.get(j - 1);
            } else {
                knapsackMsg.addFirst(0);
            }
        }
        return knapsackMsg;
    }
    
    public Map<Integer, DTNHost> getHostMapping() {
        return hostMapping;
    }
    
    public double getMeetingProb(DTNHost host) {
        MeetingEntry entry = delayTable.getIndirectMeetingEntry(getHost().getAddress(), host.getAddress());
        if (entry != null) {
            double prob = (entry.getAvgMeetingTime() * entry.getWeight()) / SimClock.getTime();
            return prob;
        }
        return 0.0;
    }
    
    public List<Duration> getListDuration(DTNHost nodes) {
        if (connHistory.containsKey(nodes)) {
            return connHistory.get(nodes);
        } else {
            List<Duration> d = new LinkedList<>();
            return d;
        }
    }
    
    public double getAverageDurationOfNodes(DTNHost nodes) {
        List<Duration> list = getListDuration(nodes);
        Iterator<Duration> duration = list.iterator();
        double hasil = 0;
        while (duration.hasNext()) {
            Duration d = duration.next();
            hasil += (d.end - d.start);
        }
        return hasil / list.size();
    }
    
    public int getTransferSpeed() {
        int speed = getHost().getInterfaces().get(0).getTransmitSpeed();
        return speed;
    }
    
    public double getTransmitRange() {
        double range = getHost().getInterfaces().get(0).getTransmitRange();
        return range;
    }
    
    public double getMovementSpeed() {
        double moveSpeed = getHost().getMovementModel().getAvgSpeed();
        return moveSpeed;
    }
    
    public double getTransmitTime() {
        return getMovementSpeed() / getTransmitRange();
    }

//    private void knapsack1(List<Message> m) {
//        int kapasitasBuffer = getHost().getRouter().getBufferSize();
////        int jumlahMsg = getHost().getRouter().getNrofMessages();
//        int jumlahMsg = m.size();
//        int i, w;
//        double bestUtility[][] = new double[jumlahMsg + 1][kapasitasBuffer + 1];
//        ArrayList<ArrayList<Double>> tes = new ArrayList<>();
//        int bestSolution[] = null;
//
//        for (i = 0; i <= jumlahMsg; i++) {
//            for (w = 0; w <= kapasitasBuffer; w++) {
//                if (i == 0 || w == 0) {
//                    bestUtility[i][w] = 0;
//                } else if (w < m.get(i - 1).getSize()) {
//                    bestUtility[i][w] = bestUtility[i - 1][w];
//                } else {
//                    int iLength = m.get(i - 1).getSize();
//                    double iUtility = (double) m.get(i - 1).getProperty("utility");
//                    bestUtility[i][w] = Math.max(bestUtility[i - 1][w], iUtility + bestUtility[i - 1][w - iLength]);
//                }
//            }
//        }
//        int tempKapBuf = kapasitasBuffer;
//        if (bestSolution == null) {
//            bestSolution = new int[jumlahMsg];
//        }
//        for (int j = jumlahMsg; j >= 1; j--) {
//            if (tempKapBuf == 0) {
//                break;
//            } else if (bestUtility[j][tempKapBuf] > bestUtility[j - 1][tempKapBuf]) {
//                bestSolution[j - 1] = 1;
//                m.get(j - 1).updateProperty("knapsack", bestSolution[j - 1]);
//                tempKapBuf = tempKapBuf - m.get(j - 1).getSize();
//            } else {
//                bestSolution[j - 1] = 0;
//                m.get(j - 1).updateProperty("knapsack", bestSolution[j - 1]);
//            }
//        }
//    }
}
