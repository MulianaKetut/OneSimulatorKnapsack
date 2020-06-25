/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package report;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimScenario;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import routing.KnapsackRouter;
import routing.MessageRouter;
import routing.RapidWithKnapsackRouter;

/**
 *
 * @author muliana_ketut
 */
public class ListMessageDiBufferReport extends Report implements MessageListener {

    public static final String NODE_ID = "ToNodeID";
    private int nodeAddress;
    private Map<DTNHost, Collection<Message>> report;
//    private Map<DTNHost, Double> avgVariance;
    private Map<DTNHost, Collection<Message>> reportData;

    public ListMessageDiBufferReport() {
        super();
        Settings s = getSettings();
        if (s.contains(NODE_ID)) {
            nodeAddress = s.getInt(NODE_ID);
        } else {
            nodeAddress = 0;
        }
        report = new HashMap<>();
        reportData = new HashMap<>();
    }

    public void done() {
        List<DTNHost> nodes = SimScenario.getInstance().getHosts();
        for (DTNHost host : nodes) {
            Map<DTNHost, Collection<Message>> nodeComm = report;
            if (host.getAddress() == nodeAddress) {
                reportData = nodeComm;

            }

        }
        String output = "Dari Node "+nodeAddress+"\nNode\tPesan\n";
        for (Map.Entry<DTNHost, Collection<Message>> entry : reportData.entrySet()) {
            DTNHost key = entry.getKey();
            Collection<Message> value = entry.getValue();
            output += key + "\t" + value + "\n";
        }
        write(output);
        super.done();
    }

    @Override
    public void newMessage(Message m) {

    }

    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {

    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {

    }

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {

    }

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
        Collection<Message> history;
        
        if (!report.containsKey(to)) {
            history = new LinkedList<Message>();

        } else {
            history = report.get(to);
        }
//        history.add((Message)(List<Message>)from.getMessageCollection());
        report.put(to, from.getMessageCollection());

    }
}
