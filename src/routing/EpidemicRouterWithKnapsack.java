/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class EpidemicRouterWithKnapsack extends ActiveRouter {

    /**
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object.
     *
     * @param s The settings object
     */
    public EpidemicRouterWithKnapsack(Settings s) {
        super(s);
        //TODO: read&use epidemic router specific settings (if any)
    }

    /**
     * Copy constructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected EpidemicRouterWithKnapsack(EpidemicRouterWithKnapsack r) {
        super(r);
        //TODO: copy epidemic settings here (if any)
    }
    
    @Override
    public void update() {
        super.update();
        if (isTransferring() || !canStartTransfer()) {
            return; // transferring, don't try other connections yet
        }

        // Try first the messages that can be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return; // started a transfer, don't try others (yet)
        }

        // then try any/all message to any/all connection
        //this.tryAllMessagesToAllConnections();
        tryOtherMessages();
    }
    
    @Override
    public EpidemicRouterWithKnapsack replicate() {
        return new EpidemicRouterWithKnapsack(this);
    }

    //private List<Message> knapsack(int kapBuffer, int sizeMsg[], int valTtlMsg[], int jumMsg) {
//    private List<Message> knapsack(Message m) {
//        int kapBuffer = this.getBufferSize();
//        int jumMsg = this.getNrofMessages();
//        //int sizeMsg[] = null;
////        sizeMsg = new int[jumMsg];
////        int valTtlMsg[];     
////        Collection<Message> msgCollection = getMessageCollection();
////        for(Message msg : msgCollection){
////            sizeMsg = m.getSize();
////        }
//
//        int i, w;
//        int bestValues[][] = new int[jumMsg + 1][kapBuffer + 1];
//        int[] keep = null;
//        List<Message> msg = new ArrayList<Message>();
//        // Build table K[][] in bottom up manner
//        for (i = 0; i <= jumMsg; i++) {
//            for (w = 0; w <= kapBuffer; w++) {
//                if (i == 0 || w == 0) {
//                    bestValues[i][w] = 0;
//
//                } else if (m.getSize() <= w) {
//
//                    bestValues[i][w] = Math.max(m.getTtl() + bestValues[i - 1][w - m.getSize()], bestValues[i - 1][w]);
//                } else {
//                    bestValues[i][w] = bestValues[i - 1][w];
//                }
//            }
//        }
//        if (keep == null) {
//            keep = new int[jumMsg];
//        }
//        int tempKapBuf = kapBuffer;
//        for (int j = jumMsg; j >= 1; j--) {
//            if (m.getSize() <= tempKapBuf) {
//                //msg.add(valTtlMsg[j-1]);
//                msg.addAll(msg);
//                keep[j - 1] = 1;
//                System.out.println("pesan yang masuk buffer dengan weight = " + m.getSize() + " dan value = " + m.getTtl());
//                tempKapBuf = tempKapBuf - m.getSize();
//            }
//        }
//        //Collections.sort(msg);
//        return msg;
//    }
    /**
     * Tries to send all other messages to all connected hosts ordered by their
     * delivery probability
     *
     * @return The return value of {@link #tryMessagesForConnected(List)}
     */
    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages
                = new ArrayList<Tuple<Message, Connection>>();
        
        Collection<Message> msgCollection = getMessageCollection();

        //  List<Message> m = new ArrayList<>(msgCollection);
        List<Message> m = msgCollection.stream().collect(Collectors.toList());
        //msgCollection.iterator().next().getTtl();

        /* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
        for (Connection con : getHost()) {
            //for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            EpidemicRouterWithKnapsack othRouter = (EpidemicRouterWithKnapsack) other.getRouter();
            
            if (othRouter.isTransferring()) {
                continue; // skip hosts that are transferring
            }
//            int ttlMsg=0;
//            for (Message m : msgCollection) {
//                if (othRouter.hasMessage(m.getId())) {
//                    continue; // skip messages that the other one has
//                }
//                if(m.getTtl()== ttlMsg){
//                    continue;
//                }
//                knapsack(m);
//                messages.add(new Tuple<Message, Connection>(m, con));
//                
//            }
//            knapsack((Message) msgCollection);
//            messages.add(new Tuple<Message, Connection>(m, con));

//            int kapBuffer = getHost().getRouter().getBufferSize();
//            int jumMsg = getHost().getRouter().getNrofMessages();
//            int i, w;
//            int bestValues[][] = new int[jumMsg + 1][kapBuffer + 1];
//            int tempKapBuf = kapBuffer;
            // Build table K[][] in bottom up manner
//            for (i = 0; i <= jumMsg; i++) {
//                for (w = 0; w <= kapBuffer; w++) {
//                    if (i == 0 || w == 0) {
//                        bestValues[i][w] = 0;
//
//                    } else if (m.get(i - 1).getSize() <= w) {
//
//                        bestValues[i][w] = Math.max(m.get(i - 1).getTtl() + bestValues[i - 1][w - m.get(i - 1).getSize()], bestValues[i - 1][w]);
//                    } else {
//                        bestValues[i][w] = bestValues[i - 1][w];
//                    }
//                }
//            }
           
            
            knapsack(getHost(), (List<Message>) m, othRouter, messages, con);

//            for (int j = jumMsg; j >= 1; j--) {
//                if (othRouter.hasMessage(m.get(j - 1).getId())) {
//                    continue; // skip messages that the other one has
//                }
//                if (bestValues[j][tempKapBuf] > bestValues[j - 1][tempKapBuf]) {
//
//                    if (m.get(j - 1).getSize() <= tempKapBuf) {
//                        //msg.add(valTtlMsg[j-1]);
////                        msg.addAll(msg);
//                        messages.add(new Tuple<Message, Connection>(m.get(j - 1), con));
//                        // System.out.println("pesan yang masuk buffer " + getHost() + " dengan weight = " + m.get(j - 1).getSize() + " dan value = " + m.get(j - 1).getTtl());
//                        tempKapBuf = tempKapBuf - m.get(j - 1).getSize();
//                    }
//                }
//            }
        }
        
        if (messages.size() == 0) {
            return null;
        }

        // sort the message-connection tuples
        Collections.sort(messages, new TupleComparator());
        return tryMessagesForConnected(messages);	// try to send messages
    }
    
    private void knapsack(DTNHost thisHost, List<Message> m, EpidemicRouterWithKnapsack othRouter, List<Tuple<Message, Connection>> messages, Connection con) {
        int kapBuffer = thisHost.getRouter().getBufferSize();
        int jumMsg = thisHost.getRouter().getNrofMessages();
        int i, w;
        int bestValues[][] = new int[jumMsg + 1][kapBuffer + 1];
        int tempKapBuf = kapBuffer;
        for (i = 0; i <= jumMsg; i++) {
            for (w = 0; w <= kapBuffer; w++) {
                if (i == 0 || w == 0) {
                    bestValues[i][w] = 0;
                    
                } else if (m.get(i - 1).getSize() <= w) {
                    
                    bestValues[i][w] = Math.max(m.get(i - 1).getTtl() + bestValues[i - 1][w - m.get(i - 1).getSize()], bestValues[i - 1][w]);
                } else {
                    bestValues[i][w] = bestValues[i - 1][w];
                }
            }
        }
        // return  bestValues[jumMsg][kapBuffer];
        for (int j = jumMsg; j >= 1; j--) {
            if (othRouter.hasMessage(m.get(j - 1).getId())) {
                continue; // skip messages that the other one has
            }
            if (bestValues[j][tempKapBuf] > bestValues[j - 1][tempKapBuf]) {
                
                if (m.get(j - 1).getSize() <= tempKapBuf) {
                    //msg.add(valTtlMsg[j-1]);
//                        msg.addAll(msg);
                    messages.add(new Tuple<Message, Connection>(m.get(j - 1), con));
                    System.out.println("pesan yang masuk buffer " + getHost() + " dengan weight = " + m.get(j - 1).getSize() + " dan value = " + m.get(j - 1).getTtl());
                    tempKapBuf = tempKapBuf - m.get(j - 1).getSize();
                }
            }
        }
    }

    /**
     * Comparator for Message-Connection-Tuples that orders the tuples by their
     * delivery probability by the host on the other side of the connection
     * (GRTRMax)
     */
    private class TupleComparator implements Comparator<Tuple<Message, Connection>> {
        
        public int compare(Tuple<Message, Connection> tuple1,
                Tuple<Message, Connection> tuple2) {
            // delivery probability of tuple1's message with tuple1's connection
//			double p1 = ((ProphetRouter)tuple1.getValue().
//					getOtherNode(getHost()).getRouter()).getPredFor(
//					tuple1.getKey().getTo());
//			// -"- tuple2...
//			double p2 = ((ProphetRouter)tuple2.getValue().
//					getOtherNode(getHost()).getRouter()).getPredFor(
//					tuple2.getKey().getTo());

            int p1 = tuple1.getKey().getTtl();
            int p2 = tuple2.getKey().getTtl();
            // bigger utility should come first
            if (p2 - p1 == 0) {
                // if (p1 - p2 == 0) {
                /* equal utility -> let queue mode decide */
                return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
            } else if (p2 - p1 < 0) {
                //} else if (p1 - p2 < 0) {
                return -1;
            } else {
                return 1;
            }
        }
    }
}
