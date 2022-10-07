package nl.knaw.huc.di.images.layoutds.DAO;

import nl.knaw.huc.di.images.layoutds.models.VectorModel;
import org.hibernate.Session;
import org.hibernate.query.Query;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.Optional;

public class VectorModelDao extends GenericDAO<VectorModel> {
    public VectorModelDao() {
        super(VectorModel.class);
    }

    public Optional<VectorModel> getByName(Session session, String model) {
        final CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
        final CriteriaQuery<VectorModel> criteriaQuery = criteriaBuilder.createQuery(VectorModel.class);
        final Root<VectorModel> from = criteriaQuery.from(VectorModel.class);
        criteriaQuery.where(criteriaBuilder.equal(from.get("model"), model));
        
        final Query<VectorModel> query = session.createQuery(criteriaQuery);

        return query.stream().findAny();
    }
}
