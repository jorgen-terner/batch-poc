#!/bin/sh
# Skriv en debug-flag så vi vet att detta körs
touch /deployments/preinit-ran

# Skriv debug-info
{
  echo "=== preinit.sh startat ==="
  date
  echo "Jar exists: $([ -r /deployments/quarkus-run.jar ] && echo YES || echo NO)"
  echo "inv-batch.sh exists: $([ -x /usr/local/bin/inv-batch.sh ] && echo YES || echo NO)"
  echo "=== End preinit ==="
} > /deployments/preinit.log 2>&1

# Anropa inv-batch.sh
exec /usr/local/bin/inv-batch.sh
