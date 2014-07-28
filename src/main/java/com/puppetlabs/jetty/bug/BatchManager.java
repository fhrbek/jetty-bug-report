package com.puppetlabs.jetty.bug;

/**
 * This is a batch manager that can launch a task several times, control runs of all tasks and wait until
 * all tasks are done. Every task is responsible for calling @taskDone on the batch manager instance when
 * it's finished.
 */
public class BatchManager {
    interface Task {
        void run(BatchManager m, int iteration);
    }

    private int counter = 0;

    private boolean running = false;

    private Thread mainThread;

    synchronized public final void taskDone() {
        if (--counter <= 0)
            mainThread.interrupt();
    }

    public final void runTask(Task t, int iterations) {
        synchronized (this) {
            if (running)
                throw new RuntimeException("There are running tasks!");

            running = true;
            counter = iterations;

            mainThread = Thread.currentThread();
        }

        for (int i = 0; i < iterations; i++) {
            t.run(this, i);
        }

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                //ignore
            }

            synchronized (this) {
                if (counter <= 0) {
                    mainThread = null;
                    running = false;
                    break;
                }
            }
        }
    }
}
