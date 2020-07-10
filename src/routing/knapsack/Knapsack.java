/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package routing.knapsack;

import core.DTNHost;
import core.Message;
import java.util.List;

/**
 *
 * @author jarkom
 */
public class Knapsack {

    public double knapsack01(DTNHost thisHost, List<Message> m) {
        int kapasitasBuffer = thisHost.getRouter().getBufferSize();
        int jumlahMsg = thisHost.getRouter().getNrofMessages();
        int i, w;
        double bestUtility[][] = new double[jumlahMsg + 1][kapasitasBuffer + 1];
        int bestSolution[] = null;
        for (i = 0; i <= jumlahMsg; i++) {
            for (w = 0; w <= kapasitasBuffer; w++) {
                if (i == 0 || w == 0) {
                    bestUtility[i][w] = 0;
                } else if (w < m.get(i - 1).getSize()) {
                    bestUtility[i][w] = bestUtility[i - 1][w];
                } else {
                    int iLength = m.get(i - 1).getSize();
                    double iUtility = (double) m.get(i - 1).getProperty("utility");
                    bestUtility[i][w] = Math.max(bestUtility[i - 1][w], iUtility + bestUtility[i - 1][w - iLength]);
                }
            }
        }
        int tempKapBuf = kapasitasBuffer;
        if (bestSolution == null) {
            bestSolution = new int[jumlahMsg];
        }
        for (int j = jumlahMsg; j >= 1; j--) {
            if (tempKapBuf == 0) {
                break;
            } else if (bestUtility[j][tempKapBuf] > bestUtility[j - 1][tempKapBuf]) {
                bestSolution[j - 1] = 1;
                m.get(j - 1).updateProperty("knapsack", bestSolution[j - 1]);
                tempKapBuf = tempKapBuf - m.get(j - 1).getSize();
            } else {
                bestSolution[j - 1] = 0;
                m.get(j - 1).updateProperty("knapsack", bestSolution[j - 1]);
            }
        }
        return bestUtility[jumlahMsg][kapasitasBuffer];
    }
}
