module com.example.bdsqltester {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.zaxxer.hikari;
    requires java.sql;
    requires org.slf4j;
    requires java.naming; // Tambahkan jika Anda menggunakan JNDI (mungkin diperlukan oleh HikariCP)
    requires org.postgresql.jdbc; // Tambahkan jika Anda menggunakan PostgreSQL JDBC driver secara modular

    opens com.example.bdsqltester to javafx.fxml;
    opens com.example.bdsqltester.dtos to javafx.base; // Baris penting yang ditambahkan

    exports com.example.bdsqltester;
    exports com.example.bdsqltester.datasources;
    opens com.example.bdsqltester.datasources to javafx.fxml;
    exports com.example.bdsqltester.scenes;
    opens com.example.bdsqltester.scenes to javafx.fxml;
    exports com.example.bdsqltester.scenes.admin;
    opens com.example.bdsqltester.scenes.admin to javafx.fxml;
    exports com.example.bdsqltester.scenes.user;
    opens com.example.bdsqltester.scenes.user to javafx.fxml;
    exports com.example.bdsqltester.dtos; // Tambahkan baris ini untuk mengekspor package dtos
}