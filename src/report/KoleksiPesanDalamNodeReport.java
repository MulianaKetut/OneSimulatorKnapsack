/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package report;

import core.ConnectionListener;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author bramchandra
 */
public class KoleksiPesanDalamNodeReport extends Report implements ConnectionListener{

    public List<DTNHost> node;
    public Map<DTNHost,Collection<Message>> messageInNode;
    public KoleksiPesanDalamNodeReport() {
        init();
    }
    public void init(){
        super.init();
        node = new ArrayList<>();
        messageInNode = new HashMap<>();
    }


    @Override
    public void hostsConnected(DTNHost host1, DTNHost host2) {        
        if(!node.contains(host1)){
            node.add(host1);
            //System.out.println("Add : "+host1);
        }
    }

    @Override
    public void hostsDisconnected(DTNHost host1, DTNHost host2) {
        
    }
    public void done(){        
        for (int i = 0; i < node.size(); i++) {
            messageInNode.put(node.get(i), node.get(i).getMessageCollection());     
            //System.out.println("NOdee : "+node.get(i) +" pesan : "+ node.get(i).getMessageCollection());
        }
        String output = "Node\tPesan\n";
        for (Map.Entry<DTNHost, Collection<Message>> entry : messageInNode.entrySet()) {
            DTNHost key = entry.getKey();
            Collection<Message> val = entry.getValue();
            output += key+"\t"+val +"\n";
        }
        write(output);
        super.done();
    }
}
