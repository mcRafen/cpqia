package it.cpqia.audit;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/** Avvolge il DataSource dell'applicazione (anche JNDI) per far arrivare l'utente corrente al trigger di journaling. */
@Component
public class AuditDataSourcePostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource ds && !(bean instanceof AuditDataSource)) {
            return new AuditDataSource(ds);
        }
        return bean;
    }
}
