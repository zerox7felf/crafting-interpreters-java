.PHONY: run genAst

genAst:
	gradle run --args=$(shell pwd)/app/src/main/java/jlox
	# Will also require you to uncomment the right mainClass in build.gradle.kts

run:
	rm -r /tmp/app
	mkdir /tmp/app/
	./gradlew build
	cp app/build/distributions/app.zip /tmp/app/
	unzip /tmp/app/app.zip -d /tmp/app/
	/tmp/app/app/bin/app test.lox
