package richtercloud.hibernate.supply.connections.feedback;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author richter
 */
public class JPADemo {
    private final static Logger LOGGER = LoggerFactory.getLogger(JPADemo.class);

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        String initdb = "/usr/lib/postgresql/9.6/bin/initdb";
        String postgres = "/usr/lib/postgresql/9.6/bin/postgres";
        String createdb = "/usr/lib/postgresql/9.6/bin/createdb";
        File databaseDir = Files.createTempDirectory(JPADemo.class.getSimpleName()).toFile();
        String databaseName = "database1";
        int databasePort = 5432;
        Process initdbProcess = new ProcessBuilder(initdb, databaseDir.getAbsolutePath())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        initdbProcess.waitFor();
        File postgresqlConfFile = new File(databaseDir, "postgresql.conf");
        try {
            Files.write(Paths.get(postgresqlConfFile.getAbsolutePath()), "\nunix_socket_directories = '/tmp'\n".getBytes(), StandardOpenOption.APPEND);
        }catch (IOException ex) {
            LOGGER.error(String.format("unexpected exception during writing to PostgreSQL configuration file '%s', see nested exception for details", postgresqlConfFile.getAbsolutePath()), ex);
        }
        Process postgresProcess = new ProcessBuilder(postgres,
                        "-D", databaseDir.getAbsolutePath(),
                        "-p", String.valueOf(databasePort))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        Process createdbProcess = new ProcessBuilder(createdb, databaseName)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        createdbProcess.waitFor();
        OutputReaderThread postgresStdoutReader = new OutputReaderThread(postgresProcess.getInputStream());
        OutputReaderThread postgresStderrReader = new OutputReaderThread(postgresProcess.getErrorStream());
        Thread postgresWatchThread = new Thread(() -> {
            try {
                int postgresReturnCode = postgresProcess.waitFor();
                postgresStdoutReader.join();
                postgresStderrReader.join();
                LOGGER.info(String.format("postgres process returned with code %d (stdout was '%s' and stderr was '%s')",
                        postgresReturnCode,
                        postgresStdoutReader.outputBuilder.toString(),
                        postgresStderrReader.outputBuilder.toString()));
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        });
        postgresStdoutReader.start();
        postgresStderrReader.start();
        postgresWatchThread.start();
        String persistenceUnitName = "persistence1";
        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnitName);
        Map<String, String> entityManagerProperties = new HashMap<>();
        String connectionURL = String.format("jdbc:postgresql://localhost:%d/%s",
                databasePort,
                databaseName);
        entityManagerProperties.put("javax.persistence.jdbc.driver",
                org.postgresql.Driver.class.getName());
        entityManagerProperties.put("javax.persistence.jdbc.url",
                connectionURL);
        entityManagerProperties.put("javax.persistence.jdbc.user",
                SystemUtils.USER_NAME);
        entityManagerProperties.put("javax.persistence.jdbc.password",
                "");
        EntityManager entityManager = entityManagerFactory.createEntityManager(entityManagerProperties);
        //store data in order to delay shutdown because of necessary writes to
        //disk
        byte[] theData = new RandomStringGenerator.Builder().build().generate(1024*1024).getBytes();
        Entity1 entity1 = new Entity1(1l,
                theData);
        entityManager.getTransaction().begin();
        entityManager.persist(entity1);
        entityManager.getTransaction().commit();
        entityManager.close();
        entityManagerFactory.close();
        entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnitName);
        entityManager = entityManagerFactory.createEntityManager(entityManagerProperties);
        entityManager.getTransaction().begin();
        entity1 = entityManager.find(Entity1.class, 1l);
        entityManager.getTransaction().commit();
        LOGGER.info(String.format("entity1.theData.length: %d",
                entity1.getTheData().length));
        entityManager.clear();
        entityManagerFactory.close();
        postgresProcess.destroy();
        LOGGER.debug("waiting for postgres process to return");
        postgresWatchThread.join();
    }

    
    private static class OutputReaderThread extends Thread {
        private final StringBuilder outputBuilder = new StringBuilder(1024);
        private final InputStream processStream;

        OutputReaderThread(InputStream processStream) {
            this.processStream = processStream;
        }
        
        @Override
        public void run() {
            try {
                BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(processStream));
                String line;
                while((line = inputStreamReader.readLine()) != null) {
                    outputBuilder.append(line).
                            append('\n');
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
