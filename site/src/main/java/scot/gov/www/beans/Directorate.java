package scot.gov.www.beans;

import org.onehippo.cms7.essentials.dashboard.annotations.HippoEssentialsGenerated;
import org.hippoecm.hst.content.beans.Node;
import scot.gov.www.beans.ContactInformation;
import java.util.List;
import org.hippoecm.hst.content.beans.standard.HippoBean;

@HippoEssentialsGenerated(internalName = "govscot:Directorate")
@Node(jcrType = "govscot:Directorate")
public class Directorate extends SimpleContent {
    @HippoEssentialsGenerated(internalName = "govscot:ContactInformation")
    public ContactInformation getContactInformation() {
        return getBean("govscot:ContactInformation", ContactInformation.class);
    }

    @HippoEssentialsGenerated(internalName = "govscot:orgRole")
    public List<HippoBean> getOrgRole() {
        return getLinkedBeans("govscot:orgRole", HippoBean.class);
    }

    @HippoEssentialsGenerated(internalName = "govscot:secondaryOrgRole")
    public HippoBean getSecondaryOrgRole() {
        return getLinkedBean("govscot:secondaryOrgRole", HippoBean.class);
    }
}
