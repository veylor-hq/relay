.PHONY: dev test test-clean build db-up db-down

dev:
	./gradlew bootRun

test:
	./gradlew test

test-clean:
	./gradlew clean test

build:
	./gradlew build

db-up:
	docker compose up -d

db-down:
	docker compose down -v
