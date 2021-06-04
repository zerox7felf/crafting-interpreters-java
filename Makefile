.PHONY: run genAst

genAst:
	echo "Have you uncommented the right mainClass definition in build.gradle.kts? (y/N):"
	if [ $(read) -n "y" ]; then
		rm $(pwd)/app/src/main/java/jlox/Expr.java
		gradle run --args=$(pwd)/app/src/main/java/jlox
	fi

run:
	rm -r /tmp/app
	mkdir /tmp/app/
	gradle build
	cp app/build/distributions/app.zip /tmp/app/
	unzip /tmp/app/app.zip -d /tmp/app/
	/tmp/app/app/bin/app
