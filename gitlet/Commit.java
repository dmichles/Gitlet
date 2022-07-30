package gitlet;

// TODO: any imports you need here

import java.io.File;
import java.io.Serializable;
import java.util.Date; // TODO: You'll likely use this in this class
import java.util.HashMap;
import java.util.List;

/** Represents a gitlet commit object.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author Daniel Michles
 */
public class Commit implements Serializable {
    /**
     * TODO: add instance variables here.
     *
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The message of this Commit. */
    private String message;
    private Date timeStamp;
    private HashMap<String,String> map;
    private String parentReference;

    private String parentReference2;

    /* TODO: fill in the rest of this class. */

    public Commit(){
        this.message = "initial commit";
        this.timeStamp = new Date(0);
        map = new HashMap<>();
        this.parentReference = null;
    }


    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

    public HashMap<String, String> getMap() {
        return map;
    }

    public void setParentReference(String parentReference) {
        this.parentReference = parentReference;
    }

    public String getParentReference() {
        return parentReference;
    }

    public String getParentReference2() {
        return parentReference2;
    }

    public void setParentReference2(String parentReference2) {
        this.parentReference2 = parentReference2;
    }

    public static Commit load(String sha1) {
        File file = Utils.join(Repository.COMMITS_DIR,sha1);
        Commit commit = Utils.readObject(file,Commit.class);
        return commit;
    }

    public void save(){
        File file = Utils.join(Repository.COMMITS_DIR, this.sha1());
        Utils.writeObject(file,this);
    }

    public String sha1(){
        return Utils.sha1(this.toString());
    }

    public String toString() {
        return message + timeStamp + map + parentReference;
    }
}
