package richtercloud.hibernate.supply.connections.feedback;

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;

/**
 *
 * @author richter
 */
@Entity
public class Entity1 implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    private Long id;
    @Basic
    @Lob
    private byte[] theData;

    protected Entity1() {
    }

    public Entity1(Long id,
            byte[] theData) {
        this.id = id;
        this.theData = theData;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public byte[] getTheData() {
        return theData;
    }

    public void setTheData(byte[] theData) {
        this.theData = theData;
    }
}
