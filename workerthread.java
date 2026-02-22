package com.learningspring.threadpool;

import java.util.concurrent.BlockingQueue;


//  WorkerThread — the actual thread that runs tasks from the queue.
// This is the "consumer" in a classic Producer-Consumer pattern.
    // MyThreadPool.submit() is the PRODUCER — it puts tasks in the queue
    // WorkerThread is the CONSUMER — it takes tasks from the queue and runs them
 
  // This pattern is everywhere in concurrent systems. Message queues, event loops, task schedulers — they all work this way at their core.
  
 
 //WHY extend Thread instead of implementing Runnable?
//  Either works, but extending Thread is clearer when we want a thread WITH its own behavior baked in. Implementing Runnable is more flexible (you can pass the same Runnable to different threads), but here each WorkerThread IS
    a specific kind of thread, so extending feels natural.
 

public class WorkerThread extends Thread {

    private final BlockingQueue<Runnable> taskQueue;

    /**
     * @param name      thread name — visible in stack traces, makes debugging easier
     * @param taskQueue shared queue this worker will pull tasks from
     *
     * WHY pass the queue in the constructor?
     *   This is dependency injection by hand. The WorkerThread doesn't create or own
     *   the queue — it's given one. Multiple workers can share the SAME queue object,
     *   which is how they all compete to pick up tasks without duplicating work.
     */
    public WorkerThread(String name, BlockingQueue<Runnable> taskQueue) {
        super(name); // sets Thread.getName() — helpful in logs and debugging
        this.taskQueue = taskQueue;
    }

   
    @Override
    public void run() {
        System.out.println("[" + getName() + "] Started, waiting for tasks...");

        // LEARN: This is the worker loop — runs forever until interrupted.
        //   !Thread.currentThread().isInterrupted() checks if someone called interrupt()
        //   on this thread. If yes, we stop the loop and the thread finishes.
        while (!Thread.currentThread().isInterrupted()) {
            try {
                //  take() BLOCKS if the queue is empty.
                //   The thread goes to sleep (doesn't consume CPU) until a task arrives.
                //   This is WAY better than a busy-wait loop like: while(queue.isEmpty()) {}
                //   which would spin and waste CPU doing nothing useful.
                //
                //   When MyThreadPool.shutdown() calls interrupt(), take() throws
                //   InterruptedException, which we catch below to exit cleanly.
                Runnable task = taskQueue.take();

                System.out.println("[" + getName() + "] Picked up task, executing...");

                // task.run() executes the actual submitted work.
                //   We wrap it in try-catch because if the task itself throws an
                //   exception, we don't want it to crash and kill the worker thread.
                //   The thread should survive task failures and keep going.
                //
                //   Real ExecutorService wraps tasks in a FutureTask and stores
                //   the exception there, so you can retrieve it via Future.get().
                //   We're just logging it here for simplicity.
                try {
                    task.run();
                    System.out.println("[" + getName() + "] Task completed.");
                } catch (Exception e) {
                    // WHY catch Exception and not Throwable?
                    //   Catching Throwable would catch OutOfMemoryError and other JVM
                    //   errors we probably can't recover from anyway. Exception is safer.
                    System.err.println("[" + getName() + "] Task threw an exception: " + e.getMessage());
                }

            } catch (InterruptedException e) {
                //  InterruptedException is thrown by take() when the thread is interrupted.
                //   This is the standard Java signal to say "stop what you're doing."
                //
                //   WHY re-interrupt? Because catching InterruptedException CLEARS the
                //   interrupt flag. If anything up the call stack checks isInterrupted(),
                //   it would see false — as if the interrupt never happened.
                //   Re-interrupting restores the flag so the while condition above
                //   (!isInterrupted()) evaluates correctly and exits the loop.
                Thread.currentThread().interrupt();
                System.out.println("[" + getName() + "] Interrupted. Shutting down this worker.");
            }
        }

        System.out.println("[" + getName() + "] Worker thread exited.");
    }
}
