
clean:
	rm -rf JDBCExplorer
	rm -rf tags

tags:
	ctags -R --langmap=java:.groovy .

all: clean
	groovyc *.groovy
