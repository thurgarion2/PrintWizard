java -cp ./:./Logging/target/classes:./InstrumentationPlugin/target/classes -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8800 BasicOperation
java -cp ./:../../../Logging/target/classes:../../../InstrumentationPlugin/target/classes  Main
##-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8800 Exemple