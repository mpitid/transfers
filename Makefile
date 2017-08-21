
.PHONY: package test run

all: package

package:
	sbt assembly

test:
	sbt test

run:
	sbt run

