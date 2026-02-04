.PHONY: all hbase clean

DC := $(shell docker compose version > /dev/null 2>&1 && echo "docker compose" || echo "docker-compose")
COMPOSE_ACTIONBASE := $(DC) -f docker-compose.yml
COMPOSE_HBASE := $(DC) -f docker-compose.local.yml

all:
	@mkdir -p data/hbase data/zookeeper data/logs
	@trap '$(COMPOSE_ACTIONBASE) down' EXIT; $(COMPOSE_ACTIONBASE) run --rm --service-ports actionbase

hbase:
	@mkdir -p data/hbase data/zookeeper data/logs
	@trap '$(COMPOSE_HBASE) down' EXIT; $(COMPOSE_HBASE) up

clean:
	$(COMPOSE_ACTIONBASE) down
	$(COMPOSE_HBASE) down
	rm -rf data/*
