

load classes ../jars/voltdb-runoncepercluster.jar;

file -inlinebatch END_OF_BATCH
   
DROP TASK BootstrapPrometheus IF EXISTS;

CREATE TASK BootstrapPrometheus  
FROM CLASS opc.ExecuteBinCommand WITH (120000,'bootstrap_prometheus.sh') 
ON ERROR LOG
RUN ON DATABASE;


END_OF_BATCH
