package com.learningspring.threadpool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


public class MyThreadPool {

    // BlockingQueue is the key data structure here.
    //   Unlike a regular Queue, BlockingQueue.take() will BLOCK (wait) if the queue
    //   is empty instead of returning null. This is perfect for worker threads —
    //   they just wait patiently until a task shows up.
    //
    //   ArrayBlockingQueue has a fixed capacity. If it's full and you try to add
    //   more, offer() returns false. We use this to signal "pool is busy."
    private final BlockingQueue<Runnable> taskQueue;

    private final List<WorkerThread> workers;

    // volatile keyword.
    //   When multiple threads read this flag, without volatile, each thread might
    //   have its own cached copy in CPU cache — they might not see the updated value.
    //   volatile forces every read/write to go to main memory, ensuring visibility
    //   across threads. This is cheaper than synchronized but only safe for
    //   single reads/writes (not compound operations like i++).
    private volatile boolean isShutdown = false;

 
     
     // : We start threads in the constructor because they need to be running
     //   before anyone submits tasks. Each WorkerThread starts blocked on
     //  taskQueue.take(), waiting for work to arrive.
   
    public MyThreadPool(int numberOfThreads, int queueCapacity) {
        this.taskQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.workers   = new ArrayList<>(numberOfThreads);

        // WHY create AND start in the same loop?
        //   We want all threads alive and waiting before the pool is handed back.
        //   If we created them in one loop and started in another, there's a tiny
        //   window where the pool looks ready but threads aren't listening yet.
        for (int i = 0; i < numberOfThreads; i++) {
            WorkerThread worker = new WorkerThread("pool-worker-" + i, taskQueue);
            workers.add(worker);
            worker.start(); // threads begin running and immediately block on taskQueue.take()
        }

        System.out.println("[MyThreadPool] Started with " + numberOfThreads + " worker threads.");
    }

    
   
     
     //This is like ExecutorService.submit(). The task goes into the queue
     //and whichever worker thread is free next will pick it up.
     
     //NOTE: We use offer() not put() here.
     //   - put()   → blocks if queue is full (caller waits)
     //   - offer() → returns false immediately if queue is full (non-blocking)
     //   We chose offer() so the caller knows immediately if the pool is overloaded.
     
     // @param task a Runnable — any piece of code you want to run asynchronously
     
    public void submit(Runnable task) {
        if (isShutdown) {
            // WHY throw here instead of silently ignoring?
            //   Fail-fast. If you submit after shutdown, something is wrong with
            //   your code logic. Better to crash loudly than silently lose work.
            throw new IllegalStateException(
                "Cannot submit tasks — thread pool has been shut down."
            );
        }

        // NOTE: offer() is non-blocking. If queue is full, this returns false.
        //   Real ExecutorService has configurable "rejection handlers" for this case.
        boolean accepted = taskQueue.offer(task);
        if (!accepted) {
            System.err.println("[MyThreadPool] WARNING: Queue is full. Task was rejected.");
        }
    }

    
    public void shutdown() {
        System.out.println("[MyThreadPool] Shutting down...");
        isShutdown = true;

        // Wait for the queue to drain before interrupting workers.
        // NOTE: This is a simple spin-wait — not ideal for production but clear to read.
        //   Real implementations use CountDownLatch or awaitTermination().
        while (!taskQueue.isEmpty()) {
            try {
                Thread.sleep(50); // give workers time to drain the queue
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Now interrupt each worker — they're blocking on take(), so this wakes them up.
        for (WorkerThread worker : workers) {
            worker.interrupt();
        }

        // Wait for all workers to actually finish.
        // WHY join()? Without it, main thread might print "shutdown complete" before
        //   workers have actually stopped. join() blocks until the thread terminates.
        for (WorkerThread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("[MyThreadPool] All workers stopped. Shutdown complete.");
    }
}
