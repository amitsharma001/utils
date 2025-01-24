package JDBCExplorer
import groovy.sql.Sql;
import groovy.time.*;
import static groovyx.gpars.GParsPool.withPool;

class PerformanceTest {
    ConnectionManager connManager;
    String query;
    List results; 
    int rowInterval = 25000;
    int threads = 1;
    int runs = 3;
    private Date testRunTime;
    private TimeDuration totalRuntime;

    public PerformanceTest(ConnectionManager conn,String query, int runs, int threads,int rowInterval) {
        connManager = conn;
        this.query = query;
        this.runs = runs;
        this.threads = threads;
        this.rowInterval = rowInterval;
    }

    private Date writeIntervalInformation(rows, tid, timeStart, lastIntervalTime) {
        def now = new Date();
        def runtime = TimeCategory.minus(now, timeStart);
        def runtimeInterval = TimeCategory.minus(now, lastIntervalTime);
        printf "Thread: %d Rows: %8d Time: %6d ms Interval Time: %6d\n", tid, rows, runtime.toMilliseconds(), runtimeInterval.toMilliseconds();
        return now
    }

    public void runTest() {
        testRunTime = new Date();
        Sql sql = connManager.getSQL();
        withPool(threads) {
            (1..runs).collectParallel() {
                def timeStart = new Date();
                Date lastIntervalTime = timeStart = null;
                int tid = Thread.currentThread().getId();
                int rows=0, cols=0;
                printf "\n*** Starting Run %d Thread %d ***\n", it, tid;
                TimeDuration runtime = null;
                try {
                    sql.query(query) { resultset -> 
                        cols = resultset.getMetaData().getColumnCount();
                        while(resultset.next()) { 
                            cols.times { resultset.getObject(it+1) }; 
                            if(rows++ == 0 || (rows % rowInterval) == 0) {
                                lastIntervalTime = writeIntervalInformation(rows, tid, timeStart, lastIntervalTime);
                            }
                        } // read all the rows
                    }
                    lastIntervalTime = writeIntervalInformation(rows, tid, timeStart, lastIntervalTime);
                    runtime = TimeCategory.minus(lastIntervalTime,timeStart);
                    printf "\n*** Run ${it} Completed Thread $tid *** Total Time: $runtime\n";
                } catch (Exception ex) {
                    println "Thread: $tid Error at row $rows: ${ex.message}";
                    runtime = new TimeDuration(0, 0, 0, 0);
                } finally {
                    sql.close();
                }
                results << [runtime, rows, cols];
            }
            totalRuntime = TimeCategory.minus(new Date(), testStartTime);
        }
    }

    public void showResults(appendToFile=true) {
        int success = 0, totalTime = 0, rows = 0, cols =0;
        results.each { result ->
            def ms = result[0].toMilliseconds();
            if(ms != 0) {
                rows = result[1];
                cols = result[2];
                success++;
                totalTime += ms;
            }
        }
        if(success > 0) {
            def avg = new TimeDuration(0,0,0,(int)(totalTime/success));
            def perfResults = "\n\n********* Performance Results: ${testRunTime} **************\n";
            perfResults <<= connManager.showDatabaseInfo();
            perfResults <<= "\nQuery: $query, Rows: $rows, Cols: $cols";
            perfResults <<= "\nRuns: $runs Threads: $threads";
            perfResults <<= "\nAverage time: ${totalTime/success} ms ($avg) per thread.";
            perfResults <<= "\nTotal time taken for $runs runs: ${totalRuntime.toMilliseconds()}ms ($totalRuntime)\n";
        }
        println perfResults;
        File fpr = new File(connManager.logdir + File.separator + connManager.driver.driver+".perf.txt")
        fpr.append(perfResults);
        fpr.close();
    }
}



