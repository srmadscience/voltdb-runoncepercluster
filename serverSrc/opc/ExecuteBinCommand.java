/* This file is part of VoltDB.
 * Copyright (C) 2021 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package opc;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.voltdb.client.ClientResponse;
import org.voltdb.task.ActionResult;
import org.voltdb.task.ActionScheduler;
import org.voltdb.task.ScheduledAction;
import org.voltdb.task.TaskHelper;

public class ExecuteBinCommand implements ActionScheduler {

    private static final long TEN_SECONDS = 10000;

    /**
     * delay in ms between cycle of job calls restarting
     */
    long m_longInterval = 120000;

    /**
     * TaskHelper is a utility class that among other things allows us to write to
     * volt.log...
     */
    TaskHelper m_helper;

    /**
     * Executable name. Must be a sh scrupt in $HOME/bin.
     */
    String m_execName = null;

    /**
     * Create a new ExecuteBinCommand. Note that 'initialize' will be called by
     * VoltDB when this is done as part of DB task.
     */
    public ExecuteBinCommand() {
        msg(TaskMessageType.DEBUG, "ExecuteBinCommand Created");
    }

    /**
     * Called as a consequence of the CREATE TASK DDL, with the 'helper' being
     * provided by VoltDB:
     * <p>
     * <code>
     * CREATE TASK ExecuteBinCommand  FROM CLASS opc.ExecuteBinCommand WITH (10,30000) ON ERROR LOG;
     * </code>
     * 
     * @param m_helper       A TaskHelper that gives us access to volt.log etc.
     * @param m_longInterval how long as set of tasks should take to run in total
     *                       (ms)
     * @param execName       an executable shell script in $HOME/bin
     */
    public void initialize(TaskHelper helper, int longInterval, String execName) {

        this.m_longInterval = longInterval;
        this.m_execName = execName;
        this.m_helper = helper;

        msg(TaskMessageType.INFO,
                "ExecuteBinCommand started with delay/execname of " + longInterval + "/" + m_execName);

    }

    /**
     * Call the simplest procedure we have - we don't acually need the output of @Ping....
     */
    @Override
    public ScheduledAction getFirstScheduledAction() {

        final Object[] m_overviewParams = {};

        // Note that we tell it to run the method 'callback' below
        // when it finishes the procedure call...
        // Also note that we are calling @Ping because we need to call *something*, not because we
        // use the results.
        return ScheduledAction.procedureCall(getLongDelay(), TimeUnit.MILLISECONDS, this::callback,
                "@Ping", m_overviewParams);
    }

    /**
     * Called when @Ping finishes. Note we pass this method in
     * as a parameter in getFirstScheduledAction(), above...
     * 
     * @param ar Results
     * @return the next thing to do.
     */
    public ScheduledAction callback(ActionResult ar) {

        if (ar.getResponse().getStatus() == ClientResponse.SUCCESS) {

            try {
                
                final long startMs = System.currentTimeMillis();
                
                File execFileName = new File(
                        System.getProperty("user.home") + File.separator + "bin" + File.separator + m_execName);

                if (execFileName.exists() && execFileName.canRead()) {

                    String actualCommand = ("sh -c " + execFileName.getAbsolutePath());

                    Process process = Runtime.getRuntime().exec(actualCommand);

                    StreamConsumer streamConsumer = new StreamConsumer(process.getInputStream(), System.out::println);
                    Executors.newSingleThreadExecutor().submit(streamConsumer);
                    int exitCode = process.waitFor();

                    if (exitCode != 0) {
                        msg(TaskMessageType.ERROR, "ExecuteBinCommand: File '" + execFileName.getAbsolutePath()
                                + "' got exit code of " + exitCode);
                    }
                    
                    if (System.currentTimeMillis() - startMs > TEN_SECONDS) {
                        msg(TaskMessageType.WARNING, "ExecuteBinCommand: File '" + execFileName.getAbsolutePath()
                        + "' took " + (System.currentTimeMillis() - startMs) +" ms to run");
                    }
                    
                } else {
                    msg(TaskMessageType.ERROR,
                            "ExecuteBinCommand: File '" + execFileName.getAbsolutePath() + "' not usable");
                }

            } catch (Exception e) {
                msg(TaskMessageType.ERROR, "ExecuteBinCommand: " + e.getMessage());

            }
            
        } else {
            
            msg(TaskMessageType.ERROR, "ExecuteBinCommand: " + ar.getResponse().getStatusString());

        }

        return getFirstScheduledAction();

    }

     /**
     * Write a message to volt.log or standard output.
     * 
     * @param messageType a type defined in TaskMessageType
     * @param message     message text
     */
    public void msg(TaskMessageType messageType, String message) {

        if (m_helper != null) {

            switch (messageType) {
            case DEBUG:
                m_helper.logDebug(message);
                break;
            case INFO:
                m_helper.logInfo(message);
                break;
            case WARNING:
                m_helper.logWarning(message);
                break;
            case ERROR:
                m_helper.logError(message);
                break;
            }

        } else {
            SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date now = new Date();
            String strDate = sdfDate.format(now);
            System.out.println(strDate + ":" + message);
        }

    }

    
    /**
     * @return delay in ms between cycle of job calls restarting
     */
    public long getLongDelay() {
        return m_longInterval;
    }

}
