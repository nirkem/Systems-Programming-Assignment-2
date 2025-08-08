package bguspl.set.ex;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import bguspl.set.Env;


/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    private Dealer dealer;
    public ArrayBlockingQueue<Integer> playerSlots;
    private int tokenCount =0;
    private boolean notify;
    public boolean isCoorectSet;
    public boolean isEmpty;


    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.playerSlots = new ArrayBlockingQueue<Integer>(env.config.featureSize);
        notify = false;
        isCoorectSet = false;
        isEmpty = false;;


    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) 
        {
            // TODO implement main player loop
            try{

                    this.tokenCount = table.PlayersToken.get(this.id).size();
                    Integer slotNum = playerSlots.take();
                    if(table.PlayersToken.get(id).contains(slotNum))
                    {
                        table.removeToken(id,slotNum);
                        tokenCount--;
                    }
                    // Nir:
                    else if(tokenCount < env.config.featureSize)
                    {
                        table.placeToken(id,slotNum);
                        tokenCount++;
                        // Nir:
                        if(tokenCount == env.config.featureSize)
                        { 
                            synchronized(this)
                            {
                                dealer.addPlayer((Integer)this.id);
                                this.wait();
                                if(!isEmpty)
                                {   
                                    if(isCoorectSet)
                                    {
                                        tokenCount = 0;
                                        this.point();
                                    }
                                    else
                                        this.penalty();
                                }

                                isCoorectSet = false;
                                isEmpty = false;
                                playerSlots.clear();

                            }
                        }
                    }
                }catch (InterruptedException ignored) {}
        }



        
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                if(playerThread.getState() != Thread.State.TIMED_WAITING){
                    // Nir:
                    int slot = (int)(Math.random()*env.config.tableSize);
                    keyPressed(slot);
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    
    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement:
        if(!human){
            aiThread.interrupt();
        }
        playerThread.interrupt();
        terminate = true;

    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement:
        try{
            playerSlots.put(slot);
        }catch(InterruptedException ignored){}
    }

   
    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        
        // TODO implement:
        env.ui.setScore(id, ++score);
        try {
            env.ui.setFreeze(id,env.config.pointFreezeMillis);
            Thread.sleep(env.config.pointFreezeMillis);
            env.ui.setFreeze(id,0);

        } catch (Exception ignored) {}   
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement:
        try {
            for(int i = 0 ; i<3; i++)
            {
                env.ui.setFreeze(id,env.config.penaltyFreezeMillis-(i*1000));
                Thread.sleep(1000);
            }
            env.ui.setFreeze(id,0);


        } catch (Exception ignored) {}
        
    }

    public int score() {
        return score;
    }

    // Aid functions


    public int getId()
    {
        return this.id;
    }

    public synchronized void setNotifyP()
    {
        synchronized(this)
        {
            notify = true;
            this.notify();
        }

    }

    public void setIsCorrectSet ()
    {
        isCoorectSet = true;
    }

    public void setIsEmpty ()
    {
        isEmpty = true;
    }

    public void StartThread()
    {
        this.playerThread = new Thread(this);
        this.playerThread.start();
    }
    public void setTokenNum (int size)
    {
        this.tokenCount = size;
    }
    public void decreaceTokenCount ()
    {
        if(this.tokenCount != 0)
        this.tokenCount--;

    }
}
