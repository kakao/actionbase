.PHONY: all clean

DC := $(shell docker compose version > /dev/null 2>&1 && echo "docker compose" || echo "docker-compose")

all:
	@mkdir -p data/hbase data/zookeeper data/logs
	@trap '$(DC) down' EXIT; $(DC) run --rm --service-ports actionbase

clean:
	$(DC) down
	rm -rf data/*
