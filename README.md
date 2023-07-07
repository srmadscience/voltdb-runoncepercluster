<img title="Volt Active Data" alt="Volt Active Data Logo" src="http://52.210.27.140:8090/voltdb-awswrangler-servlet/VoltActiveData.png">

# voltdb-runoncepercluster

A mechanism for running a script on one (and only one!) node of a cluster
# Overview
In the example below the script $HOME/bootstrap_prometheus.sh is run every 120 seconds on one of the nodes of the cluster. It checks to see if prometheus is running and starts it if it isn't. The class can therefore be used any time you need a single backlground/side process that needs to come up when the DB comes up, and be restarted if the node its on goes down.

```

CREATE TASK BootstrapPrometheus  
FROM CLASS opc.ExecuteBinCommand WITH (120000,'bootstrap_prometheus.sh') 
ON ERROR LOG
RUN ON DATABASE;
```
