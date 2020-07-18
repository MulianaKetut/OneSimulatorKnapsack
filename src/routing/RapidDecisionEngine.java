/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import routing.community.Duration;
import routing.rapidDecisionEngine.DelayEntry;
import routing.rapidDecisionEngine.DelayTable;
import routing.rapidDecisionEngine.MeetingEntry;

/**
 *
 * @author muliana_ketut
 */
public class RapidDecisionEngine implements RoutingDecisionEngineRapid {

    // timestamp for meeting a host in seconds
    private double timestamp;
    // utility algorithm (minimizing average delay | minimizing missed deadlines |
    // minimizing maximum delay)
//    private Map<Integer, DTNHost> hostMapping;
    private final UtilityAlgorithm ALGORITHM = UtilityAlgorithm.AVERAGE_DELAY;
    private static final double INFINITY = 99999;
    // interval to verify ongoing connections in seconds
    private static final double UTILITY_INTERAL = 100.0;
    private DelayTable delayTable;
    private DTNHost myHost;
//    protected Map<DTNHost, Double> startTimestamps;
//    protected Map<DTNHost, List<Duration>> connHistory;

    public RapidDecisionEngine(Settings s) {
//        delayTable = null;
        timestamp = 0.0;
//        hostMapping = new HashMap<Integer, DTNHost>();
    }

    public RapidDecisionEngine(RapidDecisionEngine de) {
        ;
        timestamp = de.timestamp;
//        startTimestamps = new HashMap<DTNHost, Double>();
//        connHistory = new HashMap<DTNHost, List<Duration>>();
    }

    @Override
    public void connectionUp(Connection con, DTNHost thisHost, DTNHost peer) {
        /* new connection */
        //simulate control channel on connection up without sending any data
        DelayTable tempDelayTable = getDelayTable();
        timestamp = SimClock.getTime();
        //synchronize all delay table entries
        this.synchronizeDelayTables(peer);
        //synchronize all meeting time entries 
        this.synchronizeMeetingTimes(peer);
        this.updateDelayTableStat(thisHost, peer);
        //synchronize acked message ids
        this.synchronizeAckedMessageIDs(peer);
        this.deleteAckedMessages(thisHost);
        //map DTNHost to their address
//            doHostMapping(con);
        tempDelayTable.dummyUpdateConnection(peer);
        this.delayTable = tempDelayTable;
    }

    @Override
    public void connectionDown(Connection con, DTNHost thisHost, DTNHost peer) {
        DelayTable tempDelayTable = getDelayTable();
        /* connection went down */
        //update connection
        double time = SimClock.getTime() - timestamp;
        tempDelayTable.updateConnection(con, time);
        //synchronize acked message ids
        synchronizeAckedMessageIDs(peer);
        //update set of messages that are known to have reached the destination 
        deleteAckedMessages(thisHost);
        updateAckedMessageIds(thisHost);
        //synchronize all delay table entries
        synchronizeDelayTables(peer);

        this.delayTable = tempDelayTable;

//        double currentTime = startTimestamps.get(peer);
//        double lastConnect = SimClock.getTime();
        // Find or create the connection history list
//        List<Duration> history;
//        if (!connHistory.containsKey(peer)) {
//            history = new LinkedList<Duration>();
//            connHistory.put(peer, history);
//        } else {
//            history = connHistory.get(peer);
//        }
//
//        // add this connection to the list
//        if (lastConnect - currentTime > 0) {
//            history.add(new Duration(currentTime, lastConnect));
//        }
//        startTimestamps.remove(peer);
    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
//        DTNHost myHost = con.getOtherNode(peer);
//        RapidDecisionEngine de = this.getOtherRapidDecisionEngine(peer);
//
//        this.startTimestamps.put(peer, SimClock.getTime());
//        de.startTimestamps.put(myHost, SimClock.getTime());

//        this.community.newConnection(myHost, peer, de.community);
    }

    @Override
    public boolean newMessage(Message m) {
        return true;
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        return m.getTo() == aHost;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        if (isFinalDest(m, thisHost)) {
            return true;
        }

        return m.getTo() != thisHost;
    }

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost) {
        DTNHost thisHost = null;

        for (Iterator<DTNHost> iterator = m.getHops().iterator(); iterator.hasNext();) {
            DTNHost next = iterator.next();

            if (next != null) {
                thisHost = next;
            }
        }

        m.updateProperty("utility", getMarginalUtility(m, otherHost, thisHost));
        double util = (double) m.getProperty("utility");
        if (util <= 0) {
            return false;
        }
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

    @Override
    public RoutingDecisionEngineRapid replicate() {
        return new RapidDecisionEngine(this);
    }

    private RapidDecisionEngine getOtherRapidDecisionEngine(DTNHost host) {
        MessageRouter otherRouter = host.getRouter();
        assert otherRouter instanceof DecisionEngineRapidRouter : "This router only works "
                + " with other routers of same type";

        return (RapidDecisionEngine) ((DecisionEngineRapidRouter) otherRouter).getDecisionEngine();
    }

    /**
     * protected DTNHost getHost() {
     *
     * return this.host; }
     */
    @Override
    public void update(Message m, DTNHost host, DTNHost from, String status) {

        if (status.equals("create")) {

            updateDelayTableEntry(m, host, estimateDelay(m, host, true), SimClock.getTime());
            m.updateProperty("utility", estimateDelay(m, host, true));

        } else if (status.equals("receive")) {
            double time = SimClock.getTime();
            double delay = estimateDelay(m, host, true);
            this.updateDelayTableEntry(m, host, delay, time);
            RapidDecisionEngine otherDecisionEngine = getOtherRapidDecisionEngine(from);
            otherDecisionEngine.updateDelayTableEntry(m, host, delay, time);

        }

    }

    @Override
    public void ckeckConnectionStatus(DTNHost thisHost) {
        DelayTable tempDelayTable = getDelayTable();
        //DTNHost host = getHost();
        int from = thisHost.getAddress();
        double checkPeriod = 0.0;
        DecisionEngineRapidRouter myRouter = (DecisionEngineRapidRouter) thisHost.getRouter();

        for (Connection con : myRouter.getConnections()) {
            DTNHost other = con.getOtherNode(thisHost);
            int to = other.getAddress();

            RapidDecisionEngine otherDecisionEngine = getOtherRapidDecisionEngine(other);
            MeetingEntry entry = otherDecisionEngine.getDelayTable().getMeetingEntry(from, to);
            checkPeriod = SimClock.getTime() - UTILITY_INTERAL;

            if (con.isUp() && entry.isOlderThan(checkPeriod)) {
                // simulate artificial break 
                //update connection
                double time = (SimClock.getTime() - 0.1) - this.timestamp;
                tempDelayTable.updateConnection(con, time);

                //synchronize acked message ids
                synchronizeAckedMessageIDs(other);

                //update set of messages that are known to have reached the destination 
                deleteAckedMessages(thisHost);
                updateAckedMessageIds(thisHost);

                //synchronize all delay table entries
                synchronizeDelayTables(other);

                // simulate artificial make
                //simulate control channel on connection up without sending any data
                timestamp = SimClock.getTime();

                //synchronize all delay table entries
                synchronizeDelayTables(other);

                //synchronize all meeting time entries 
                synchronizeMeetingTimes(other);

                updateDelayTableStat(thisHost, other);

                //synchronize acked message ids
                synchronizeAckedMessageIDs(other);

                deleteAckedMessages(thisHost);

                //map DTNHost to their address
                //doHostMapping(con);
                tempDelayTable.dummyUpdateConnection(other);

            }
        }
        this.delayTable = tempDelayTable;
    }

    public void setDelayTable(DTNHost host) {
        DecisionEngineRapidRouter router = (DecisionEngineRapidRouter) host.getRouter();
        this.delayTable = router.delayTable;
    }

    private DelayTable getDelayTable() {
        return this.delayTable;
    }

    @Override
    public Collection<Message> sortMessage(Collection<Message> msgCollection) {
        Collections.sort((List<Message>) msgCollection,
                new Comparator() {
            public int compare(Object u1, Object u2) {
                double diff;
                Message m1, m2;

                if (u1 instanceof Tuple) {
                    m1 = ((Tuple<Message, Connection>) u1).getKey();
                    m2 = ((Tuple<Message, Connection>) u2).getKey();
                } else if (u1 instanceof Message) {
                    m1 = (Message) u1;
                    m2 = (Message) u2;
                } else {
                    throw new SimError("Invalid type of objects in "
                            + "the list");
                }
                diff = (double) m2.getProperty("utility") - (double) m1.getProperty("utility");
                if (diff == 0) {
                    return (m1.hashCode() / 2 + m2.hashCode() / 2) % 3 - 1;
                } else if (diff < 0) {
                    return -1;
                } else {
                    return 1;
                }
            }

        });
        return msgCollection;
    }

    private enum UtilityAlgorithm {
        AVERAGE_DELAY,
        MISSED_DEADLINES,
        MAXIMUM_DELAY;
    }

//    public DelayTable getDelayTable() {
//        return delayTable;
//    }
//    public List<Duration> getListDuration(DTNHost nodes) {
//        if (connHistory.containsKey(nodes)) {
//            return connHistory.get(nodes);
//        } else {
//            List<Duration> d = new LinkedList<>();
//            return d;
//        }
//    }
//
//    public double getAverageDurationOfNodes(DTNHost nodes) {
//        List<Duration> list = getListDuration(nodes);
//        Iterator<Duration> duration = list.iterator();
//        double hasil = 0;
//        while (duration.hasNext()) {
//            Duration d = duration.next();
//            hasil += (d.end - d.start);
//        }
//        return hasil / list.size();
//    }
    private void updateDelayTableStat(DTNHost thisHost, DTNHost otherHost) {
        DelayTable tempDelayTable = getDelayTable();
//        DTNHost otherHost = con.getOtherNode(getHost());
        RapidDecisionEngine otherRouter = getOtherRapidDecisionEngine(otherHost);
        int from = otherHost.getAddress();

        for (Message m : thisHost.getMessageCollection()) {
            int to = m.getTo().getAddress();
            MeetingEntry entry = otherRouter.getDelayTable().getMeetingEntry(from, to);
            if (entry != null) {
                tempDelayTable.getDelayEntryByMessageId(m.getId()).setChanged(true);
            }
        }
        this.delayTable = tempDelayTable;
    }

    private void synchronizeDelayTables(DTNHost otherHost) {
        DelayTable tempDelayTable = getDelayTable();
        //DTNHost otherHost = con.getOtherNode(getHost());
        RapidDecisionEngine otherRouter = getOtherRapidDecisionEngine(otherHost);
        DelayEntry delayEntry = null;
        DelayEntry otherDelayEntry = null;

        //synchronize all delay table entries
        for (Entry<String, DelayEntry> entry1 : tempDelayTable.getDelayEntries()) {
            Message m = entry1.getValue().getMessage();
            delayEntry = tempDelayTable.getDelayEntryByMessageId(m.getId());
            assert (delayEntry != null);
            otherDelayEntry = otherRouter.getDelayTable().getDelayEntryByMessageId(m.getId());

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
        this.delayTable = tempDelayTable;
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
        DelayTable tempDelayTable = getDelayTable();
        DelayEntry delayEntry;

        if ((delayEntry = tempDelayTable.getDelayEntryByMessageId(m.getId())) == null) {
            delayEntry = new DelayEntry(m);
            tempDelayTable.addEntry(delayEntry);
        }
        assert ((delayEntry != null) && (tempDelayTable.getDelayEntryByMessageId(m.getId()) != null));

        if (delayEntry.contains(host)) {
            delayEntry.setHostDelay(host, delay, time);
        } else {
            delayEntry.addHostDelay(host, delay, time);
        }
        this.delayTable = tempDelayTable;
    }

    private void synchronizeMeetingTimes(DTNHost otherHost) {
        DelayTable tempDelayTable = getDelayTable();
        //DTNHost otherHost = con.getOtherNode(getHost());
        RapidDecisionEngine otherRouter = getOtherRapidDecisionEngine(otherHost);
        MeetingEntry meetingEntry = null;
        MeetingEntry otherMeetingEntry = null;

        //synchronize all meeting time entries
        for (int i = 0; i < tempDelayTable.getMeetingMatrixDimension(); i++) {
            for (int k = 0; k < tempDelayTable.getMeetingMatrixDimension(); k++) {
                meetingEntry = tempDelayTable.getMeetingEntry(i, k);

                if (meetingEntry != null) {
                    otherMeetingEntry = otherRouter.getDelayTable().getMeetingEntry(i, k);
                    //create a new meeting entry if meeting entry at other host doesn't exist
                    if (otherMeetingEntry == null) {
                        otherRouter.getDelayTable().setAvgMeetingTime(i, k, meetingEntry.getAvgMeetingTime(), meetingEntry.getLastUpdate(), meetingEntry.getWeight());
                    } else {
                        //check last update time of other hosts entry and update it 
                        if (otherMeetingEntry.isOlderThan(meetingEntry.getLastUpdate())) {
                            otherRouter.getDelayTable().setAvgMeetingTime(i, k, meetingEntry.getAvgMeetingTime(), meetingEntry.getLastUpdate(), meetingEntry.getWeight());
                        }

                        if ((otherMeetingEntry.isAsOldAs(meetingEntry.getLastUpdate())) && (meetingEntry.getAvgMeetingTime() > otherMeetingEntry.getAvgMeetingTime())) {
                            otherRouter.getDelayTable().setAvgMeetingTime(i, k, meetingEntry.getAvgMeetingTime(), meetingEntry.getLastUpdate(), meetingEntry.getWeight());
                        }
                    }
                }
            }
        }
        this.delayTable = tempDelayTable;
    }

    private void synchronizeAckedMessageIDs(DTNHost otherHost) {
        DelayTable tempDelayTable = getDelayTable();
        //DTNHost otherHost = con.getOtherNode(getHost());
        RapidDecisionEngine otherRouter = getOtherRapidDecisionEngine(otherHost);

        tempDelayTable.addAllAckedMessageIds(otherRouter.getDelayTable().getAllAckedMessageIds());
        otherRouter.getDelayTable().addAllAckedMessageIds(tempDelayTable.getAllAckedMessageIds());

        assert (tempDelayTable.getAllAckedMessageIds().equals(otherRouter.getDelayTable().getAllAckedMessageIds()));
        this.delayTable = tempDelayTable;
    }

    /**
     * Deletes the messages from the message buffer that are known to be ACKed
     * and also delete the message entries of the ACKed messages in the delay
     * table
     */
    private void deleteAckedMessages(DTNHost thisHost) {
        DelayTable tempDelayTable = getDelayTable();
        DecisionEngineRapidRouter thisRouter = (DecisionEngineRapidRouter) thisHost.getRouter();
        for (String id : tempDelayTable.getAllAckedMessageIds()) {
            if (thisRouter.hasMessage(id) && !thisRouter.isSending(id)) {
                //delete messages from the message buffer
                thisRouter.deleteMessage(id, false);
            }

            //delete messages from the delay table
            if (tempDelayTable.getDelayEntryByMessageId(id) != null) {
                assert (tempDelayTable.removeEntry(id) == true);
            }
        }
        this.delayTable = tempDelayTable;
    }

    /**
     * Delete old message ids from the acked message id set which time to live
     * was passed
     */
    private void updateAckedMessageIds(DTNHost thisHost) {
        DelayTable tempDelayTable = getDelayTable();
        ArrayList<String> removableIds = new ArrayList<String>();

        for (String id : tempDelayTable.getAllAckedMessageIds()) {
            Message m = thisHost.getRouter().getMessage(id);
            if ((m != null) && (m.getTtl() <= 0)) {
                removableIds.add(id);
            }
        }

        if (removableIds.size() > 0) {
            tempDelayTable.removeAllAckedMessageIds(removableIds);
        }
        this.delayTable = tempDelayTable;
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
    private double getMarginalUtility(Message msg, DTNHost peer, DTNHost host) {

//        final RapidDecisionEngine otherRouter = con.getOtherNode(host).getRouter();
        final RapidDecisionEngine otherRouter = getOtherRapidDecisionEngine(peer);

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
    private double getMarginalUtility(Message msg, RapidDecisionEngine router, DTNHost host) {
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

    private double computeUtility(Message msg, DTNHost thisHost, boolean recompute) {
        double utility = 0.0;		// U(i): The utility of the message (packet) i
        double packetDelay = 0.0;	// D(i): The expected delay of message i

        switch (ALGORITHM) {
            // minimizing average delay
            // U(i) = -D(i)
            case AVERAGE_DELAY: {
                packetDelay = estimateDelay(msg, thisHost, recompute);
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
                double remainingTime = computeRemainingTime(msg, thisHost);
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
                packetDelay = computePacketDelay(msg, thisHost, remainingTime);
                break;
            }
            // minimizing maximum delay
            // S   : Set of all message in buffer of X
            //
            // 		   / -D(i) , D(i) >= D(j) for all j element of S
            // U(i) = <|
            // 		   \   0   , otherwise
            case MAXIMUM_DELAY: {
                packetDelay = estimateDelay(msg, thisHost, recompute);
                Collection<Message> msgCollection = thisHost.getMessageCollection();
                for (Message m : msgCollection) {
                    if (m.equals(msg)) {
                        continue;	//skip 
                    }
                    if (packetDelay < estimateDelay(m, thisHost, recompute)) {
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

    /*
	 * Estimate-Delay Algorithm
	 * 
	 * 1) Sort packets in Q in decreasing order of T(i). Let b(i) be the sum of 
	 * sizes of packets that precede i, and B the expected transfer opportunity 
	 * in bytes between X and Z.
	 * 
	 * 2. X by itself requires b(i)/B meetings with Z to deliver i. Compute the 
	 * random variable MX(i) for the corresponding delay as
	 * MX(i) = MXZ + MXZ + . . . b(i)/B times (4)
	 *  
	 * 3. Let X1 , . . . , Xk ⊇ X be the set of nodes possessing a replica of i. 
	 * Estimate remaining time a(i) as  
	 * a(i) = min(MX1(i), . . . , MXk(i))
	 * 
	 * 4. Expected delay D(i) = T(i) + E[a(i)]
     */
    /**
     * Returns the expected delay for a message if this message was sent to a
     * specified host
     *
     * @param msg The message which will be transfered
     * @return the expected packet delay
     */
    private double estimateDelay(Message msg, DTNHost thisHost, boolean recompute) {
        DelayTable tempDelayTable = getDelayTable();
        double remainingTime = 0.0;	//a(i): random variable that determines the remaining time to deliver message i
        double packetDelay = 0.0;	//D(i): expected delay of message i

        //if delay table entry for this message doesn't exist or the delay table
        //has changed recompute the delay entry
        if ((recompute) && ((tempDelayTable.delayHasChanged(msg.getId())) || (tempDelayTable.getDelayEntryByMessageId(msg.getId()).getDelayOf(thisHost) == null) /*|| (delayTable.getEntryByMessageId(msg.getId()).getDelayOf(host) == INFINITY))*/)) {
            // compute remaining time by using metadata
            remainingTime = computeRemainingTime(msg, thisHost);
            packetDelay = Math.min(INFINITY, computePacketDelay(msg, thisHost, remainingTime));

            //update delay table
            updateDelayTableEntry(msg, thisHost, packetDelay, SimClock.getTime());
        } else {
            packetDelay = tempDelayTable.getDelayEntryByMessageId(msg.getId()).getDelayOf(thisHost);
        }

        this.delayTable = tempDelayTable;
        return packetDelay;
    }

    private double computeRemainingTime(Message msg, DTNHost thisHost) {
        DelayTable tempDelayTable = getDelayTable();
        double transferTime = INFINITY;		//MX(i):random variable for corresponding transfer time delay
        double remainingTime = 0.0;		//a(i): random variable that determines the	remaining time to deliver message i

        remainingTime = computeTransferTime(msg, thisHost, msg.getTo());
        if (tempDelayTable.getDelayEntryByMessageId(msg.getId()) != null) {
            //Entry<DTNHost host, Tuple<Double delay, Double lastUpdate>>
            for (Entry<DTNHost, Tuple<Double, Double>> entry : tempDelayTable.getDelayEntryByMessageId(msg.getId()).getDelays()) {
                DTNHost otherHost = entry.getKey();
                if (otherHost == thisHost) {
                    continue;	// skip
                }
                transferTime = getOtherRapidDecisionEngine(otherHost).computeTransferTime(msg, thisHost, msg.getTo()); //MXm(i)	with m element of [0...k]
                remainingTime = Math.min(transferTime, remainingTime);	//a(i) = min(MX0(i), MX1(i), ... ,MXk(i)) 
            }
        }
        this.delayTable = tempDelayTable;
        return remainingTime;
    }

    private double computeTransferTime(Message msg, DTNHost thisHost, DTNHost otherHost) {
        DelayTable tempDelayTable = getDelayTable();
        Collection<Message> msgCollection = thisHost.getMessageCollection();
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
        transferOpportunity = tempDelayTable.getAvgTransferOpportunity();	//B in bytes

        MeetingEntry entry = tempDelayTable.getIndirectMeetingEntry(thisHost.getAddress(), otherHost.getAddress());
        // a meeting entry of null means that these hosts have never met -> set transfer time to maximum
        if (entry == null) {
            transferTime = INFINITY;		//MX(i)
        } else {
            meetingTime = entry.getAvgMeetingTime();	//MXZ
            transferTime = meetingTime * Math.ceil(packetsSize / transferOpportunity);	// MX(i) = MXZ * ceil[b(i) / B]
        }
        this.delayTable = tempDelayTable;
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
}
