
clean:
	rm -rf JDBCExplorer
	rm -rf tags

tags:
	ctags -R --langmap=java:.groovy .

all: clean
	groovyc *.groovy

runjava: all
	groovy ConsolePrompt.groovy

runpython:
	cd PythonExplorer && source venv/bin/activate && python console_prompt.py
