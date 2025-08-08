package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Collections;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;


    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    Thread[] playerThreads;
    // Nir:
    boolean hints;


    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private ArrayBlockingQueue<Integer> playerIds;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.playerIds = new ArrayBlockingQueue<Integer>(env.config.players);
        this.playerThreads = new Thread[players.length];
        // Nir:
        this.hints = env.config.hints;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player playerTemp: players ) 
        {
            playerTemp.StartThread();    
        }
        while (!shouldFinish()) 
        {
            Collections.shuffle(deck);
            placeCardsOnTable();
            // Nir:
            if (this.hints)
                this.table.hints();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        terminate();
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        for (int i = players.length - 1; i >= 0; i--) 
            players[i].terminate();
        terminate = true;  

    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(List<Integer> slots) {
        List<Integer> slotsTemp = new ArrayList<Integer>(slots);
        for(int slot : slotsTemp) 
        {
            table.removeCard(slot);
        }    
    }
     
    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        for( int i = 0; i< (table.slotToCard).length ;i++)
        {
            if(table.slotToCard[i] == null)
            {
                if(deck.size()> 0)
                {
                    int card = deck.get(0);
                    table.placeCard(card,i);
                    deck.remove(0);
                    updateTimerDisplay(true);

                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
        // TODO implement
        Integer idPlayer = null;
        try{ 
           //Nir 
           idPlayer = this.playerIds.poll(10,TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored){}
       
        if(idPlayer!=null)
        {
            //to add 
            Player tempPlayer = players[idPlayer];

            if(table.PlayersToken.get(idPlayer).size() < 3)
            {
                tempPlayer.setIsEmpty();
                tempPlayer.setTokenNum(table.PlayersToken.get(idPlayer).size());
                tempPlayer.setNotifyP();
            }
            else if(checkTokens(tempPlayer))
            {
                tempPlayer.setIsCorrectSet();
                tempPlayer.setNotifyP();
            }
            tempPlayer.setNotifyP();

        }
        
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset)
        {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }        
        if(reshuffleTime - System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis)
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);  
        else
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        //Talya's line:
        // env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(),false);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        
        Integer tempCard;
        for(int i = 0; i< table.slotToCard.length; i++)
        {
            if(table.slotToCard[i] != null)
            {
                tempCard = table.slotToCard[i];
                deck.add(tempCard);
                table.removeCard(i);
            }
            
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int maxScore = 0;
        int winnerId = 1;
        for (Player i : players){ // search for highest score -should consider tie
            if (maxScore < i.score())
            {
                maxScore = i.score();
                winnerId =1;
            }
            else if(maxScore == i.score())
            {
                winnerId ++;
            }
        }
        int [] winners = new int[winnerId];
        for (Player curr : players){
            if(curr.score() == maxScore){
                winnerId--;
                winners[winnerId] = curr.id;
            }
        }
        env.ui.announceWinner(winners);
    }

 

    public boolean checkTokens(Player player)
    {
        List<Integer> tokenPlayer= table.PlayersToken.get(player.getId());
        int[] arrayTokens = new int[3];
        for (int i = 0; i<3; i++)
        {
            arrayTokens[i] = table.slotToCard[tokenPlayer.get(i)];
        }
        if(env.util.testSet(arrayTokens))
        {

            removeCardsFromTable(tokenPlayer);
            placeCardsOnTable();
            //table.clearTokens(tokenPlayer.get(0));
            //table.clearTokens(tokenPlayer.get(1));
            //table.clearTokens(tokenPlayer.get(2));
            return true;
        }
        else 
            return false;
        

    }

    public  void addPlayer(int id){
        try{
            playerIds.put(id);
        }catch(InterruptedException ignored){}    
    }

}
