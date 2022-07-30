package gitlet;

import java.io.File;
import java.io.Serializable;

public class Head implements Serializable {
    private String branch;
    private String commitReference;

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getCommitReference() {
        return commitReference;
    }

    public void setCommitReference(String commitReference) {
        this.commitReference = commitReference;
    }

    public static Head load(){
        File file = Utils.join(Repository.GITLET_DIR,Repository.HEAD_FILE);
        Head head = Utils.readObject(file,Head.class);
        return head;
    }

    public void save(){
        File file = Utils.join(Repository.GITLET_DIR,Repository.HEAD_FILE);
        Utils.writeObject(file,this);
    }
}
