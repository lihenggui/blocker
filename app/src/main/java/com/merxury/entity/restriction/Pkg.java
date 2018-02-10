package com.merxury.entity.restriction;

import java.util.List;

/**
 * Created by Mercury on 2018/2/2.
 */

public class Pkg {
    private boolean stopped;
    private int ceDataInode;
    private int appLinkGeneration;
    private int domainVerificationStatus;
    private boolean nl;
    private String name;
    private List<Item> enabledComponents;
    private List<Item> disabledComponents;
}
